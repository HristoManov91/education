package bg.hristomanov.education.consistency.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.InvalidProducerEpochException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Actual broker/DB, deliberate failure injection between independent commits.
 * No claim of XA or Spring transaction-manager synchronization test.
 */
@Timeout(120)
class KafkaCommitWindowTest {
    private static final String DB_FIRST = "db-first";
    private static final String KAFKA_FIRST = "kafka-first";
    private static EmbeddedKafkaBroker broker;
    @TempDir Path directory;
    private JdbcDataSource db;

    @BeforeAll static void startBroker() throws Exception {
        broker = new EmbeddedKafkaKraftBroker(1, 1, DB_FIRST, KAFKA_FIRST)
                .brokerProperties(Map.of(
                        "transaction.state.log.replication.factor","1",
                        "transaction.state.log.min.isr","1",
                        "offsets.topic.replication.factor","1"));
        broker.afterPropertiesSet();
    }
    @AfterAll static void stopBroker() throws Exception {
        if (broker != null) broker.destroy();
    }
    @BeforeEach void createDatabase() throws SQLException {
        db = new JdbcDataSource();
        db.setURL("jdbc:h2:file:" + directory.resolve("orders").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        db.setUser("sa");
        try(var connection=db.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE orders (order_id VARCHAR(100) PRIMARY KEY)");
        }
    }

    @Test void dbFirstThenFencedKafkaCommitLeavesCommittedRowWithoutCommittedEvent() throws Exception {
        try (var connection=db.getConnection(); var old=producer("shared-db-first")) {
            connection.setAutoCommit(false);
            insert(connection,"order-db-first");
            old.initTransactions();
            old.beginTransaction();
            send(old,DB_FIRST,"order-db-first");
            connection.commit(); // independent durable DB commit; cannot be undone by broker.
            assertEquals(1,countOrders());

            // A replacement producer using the same transactional.id fences old epoch and aborts
            // the previous unfinished transaction. A committed marker gives the consumer a positive oracle.
            try(var replacement=producer("shared-db-first")) {
                replacement.initTransactions();
                var failure=assertThrows(KafkaException.class,old::commitTransaction);
                assertTrue(fenced(failure), "Expected epoch fencing, got " + failure);
                replacement.beginTransaction();
                send(replacement,DB_FIRST,"marker");
                replacement.commitTransaction();
            }
            assertEquals(1,countOrders());
        }
        assertEquals(List.of("marker"),readThrough(DB_FIRST,"read_committed","marker"));
        assertEquals(List.of("order-db-first","marker"),readThrough(DB_FIRST,"read_uncommitted","marker"));
    }

    @Test void kafkaFirstThenLostDbConnectionLeavesCommittedEventWithoutOrderRow() throws Exception {
        try(var connection=db.getConnection(); var producer=producer("kafka-first")) {
            connection.setAutoCommit(false);
            insert(connection,"order-kafka-first");
            producer.initTransactions();
            producer.beginTransaction();
            send(producer,KAFKA_FIRST,"order-kafka-first");
            producer.commitTransaction();
            // Controlled H2 connection loss before DB commit; closing an uncommitted H2
            // connection rolls back its local transaction. No simulation of uncertain remote commit.
            connection.close();
            assertThrows(SQLException.class,connection::commit);
        }
        assertEquals(0,countOrders());
        assertEquals(List.of("order-kafka-first"),readThrough(KAFKA_FIRST,"read_committed","order-kafka-first"));
    }

    private KafkaProducer<String,String> producer(String id) {
        var properties=new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBrokersAsString());
        properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,id);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class.getName());
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,"true");
        properties.put(ProducerConfig.ACKS_CONFIG,"all");
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,"30000");
        properties.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG,"60000");
        return new KafkaProducer<>(properties);
    }
    private static void send(KafkaProducer<String,String> producer,String topic,String value) throws Exception {
        producer.send(new ProducerRecord<>(topic,0,"aggregate",value)).get(20,TimeUnit.SECONDS);
    }
    private static boolean fenced(Throwable error) {
        for(Throwable current=error;current!=null;current=current.getCause()) {
            if(current instanceof ProducerFencedException || current instanceof InvalidProducerEpochException) return true;
            if(current.getCause()==current) break;
        }
        return false;
    }

    private List<String> readThrough(String topic,String isolation,String finalValue) {
        var props=new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBrokersAsString());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,"false");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,isolation);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,"30000");
        try(var consumer=new KafkaConsumer<String,String>(props)) {
            var partition=new TopicPartition(topic,0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            var seen=new ArrayList<String>();
            long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
            while(System.nanoTime()<deadline) {
                for(var record:consumer.poll(Duration.ofMillis(250))) seen.add(record.value());
                if(seen.contains(finalValue)) return List.copyOf(seen);
            }
            fail("Did not reach committed marker/event for " + topic + ": " + seen);
            return List.of();
        }
    }
    private static void insert(Connection connection,String id) throws SQLException {
        try(var statement=connection.prepareStatement("INSERT INTO orders VALUES (?)")) {
            statement.setString(1,id); statement.executeUpdate();
        }
    }
    private int countOrders() throws SQLException {
        try(var connection=db.getConnection();var statement=connection.createStatement();
            var result=statement.executeQuery("SELECT COUNT(*) FROM orders")) {
            assertTrue(result.next()); return result.getInt(1);
        }
    }
}
