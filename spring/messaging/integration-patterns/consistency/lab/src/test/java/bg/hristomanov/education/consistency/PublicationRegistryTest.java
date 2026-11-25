package bg.hristomanov.education.consistency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
public class PublicationRegistryTest {
    @TempDir Path directory;

    // Public record with stable serialized fields, no event instance kept across contexts.
    public record OrderPlaced(String orderId) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableAsync
    public static class App {
        @Bean Source source(JdbcTemplate jdbc, ApplicationEventPublisher publisher) { return new Source(jdbc,publisher); }
        @Bean Listener listener(JdbcTemplate jdbc) { return new Listener(jdbc); }
    }

    public static class Source {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        Source(JdbcTemplate jdbc, ApplicationEventPublisher publisher) { this.jdbc=jdbc; this.publisher=publisher; }

        @Transactional
        public void place(String id, boolean rollback) {
            jdbc.update("INSERT INTO placed_order VALUES (?)",id);
            publisher.publishEvent(new OrderPlaced(id));
            if (rollback) throw new IllegalStateException("source rollback");
        }
    }

    public static class Listener {
        private final JdbcTemplate jdbc;
        private final AtomicBoolean fail = new AtomicBoolean();
        Listener(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        public void failNextAttempts(boolean value) { fail.set(value); }

        @ApplicationModuleListener
        public void project(OrderPlaced event) {
            // Absolute projection by stable business key, idempotent even if listener is redelivered.
            jdbc.update("MERGE INTO order_projection (order_id, state) KEY(order_id) VALUES (?, 'PLACED')",event.orderId());
            if (fail.get()) throw new IllegalStateException("listener failure after SQL");
        }
    }

    private ConfigurableApplicationContext open() {
        var url = "jdbc:h2:file:" + directory.resolve("registry").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        var context = new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE)
                .properties("spring.datasource.url="+url, "spring.datasource.username=sa",
                        "spring.modulith.events.jdbc.schema-initialization.enabled=true",
                        "spring.modulith.events.jdbc.use-legacy-structure=false",
                        "spring.modulith.events.republish-outstanding-events-on-restart=false",
                        "spring.modulith.events.completion-mode=update",
                        "spring.main.banner-mode=off")
                .run();
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE IF NOT EXISTS placed_order (order_id VARCHAR(100) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS order_projection (order_id VARCHAR(100) PRIMARY KEY, state VARCHAR(20) NOT NULL)");
        return context;
    }
    private static int count(JdbcTemplate jdbc,String sql) { return jdbc.queryForObject(sql,Integer.class); }

    @Test void sourceRollbackRemovesBusinessRowAndRegistryEntry() {
        try(var context = open()) {
            var jdbc=context.getBean(JdbcTemplate.class);
            assertThrows(IllegalStateException.class,()->context.getBean(Source.class).place("rollback",true));
            assertEquals(0,count(jdbc,"SELECT COUNT(*) FROM placed_order"));
            assertEquals(0,count(jdbc,"SELECT COUNT(*) FROM event_publication"));
            assertEquals(0,count(jdbc,"SELECT COUNT(*) FROM order_projection"));
        }
    }

    @Test void failedListenerRollsBackProjectionAndPersistentRegistryRecoversAfterContextRestart() {
        try(var first = open()) {
            var jdbc=first.getBean(JdbcTemplate.class);
            first.getBean(Listener.class).failNextAttempts(true);
            first.getBean(Source.class).place("order-1",false);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM event_publication WHERE status = 'FAILED'"));
                assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM placed_order"));
                assertEquals(0,count(jdbc,"SELECT COUNT(*) FROM order_projection"));
            });
        }
        // Same file-backed DB, new context, no in-memory event or retry flag reused.
        try(var second = open()) {
            var jdbc=second.getBean(JdbcTemplate.class);
            assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM event_publication WHERE status = 'FAILED'"));
            assertEquals(0,count(jdbc,"SELECT COUNT(*) FROM order_projection"));
            var failed=second.getBean(FailedEventPublications.class);
            failed.resubmit(ResubmissionOptions.defaults().withBatchSize(10).withMaxInFlight(1));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM event_publication WHERE status = 'COMPLETED'"));
                assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM order_projection WHERE order_id='order-1' AND state='PLACED'"));
                assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM placed_order"));
            });
            failed.resubmit(ResubmissionOptions.defaults().withBatchSize(10));
            assertEquals(1,count(jdbc,"SELECT COUNT(*) FROM order_projection"));
            assertEquals(2,jdbc.queryForObject("SELECT completion_attempts FROM event_publication",Integer.class));
        }
    }
}
