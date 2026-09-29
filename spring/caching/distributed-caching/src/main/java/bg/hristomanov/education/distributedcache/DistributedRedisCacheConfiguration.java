package bg.hristomanov.education.distributedcache;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class DistributedRedisCacheConfiguration {

    public static final Duration PRODUCT_TTL =
            Duration.ofMinutes(5);

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        GenericJacksonJsonRedisSerializer valueSerializer =
                GenericJacksonJsonRedisSerializer
                        .builder()
                        .build();

        RedisCacheConfiguration configuration =
                RedisCacheConfiguration
                        .defaultCacheConfig()
                        .entryTtl(PRODUCT_TTL)
                        .disableCachingNullValues()
                        .prefixCacheNameWith("edu:")
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        )
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(valueSerializer)
                        );

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    @Bean
    public RedisTemplate<String, Product> productRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Product> template =
                new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(
                new StringRedisSerializer()
        );
        template.setValueSerializer(
                new JacksonJsonRedisSerializer<>(Product.class)
        );
        template.afterPropertiesSet();

        return template;
    }
}
