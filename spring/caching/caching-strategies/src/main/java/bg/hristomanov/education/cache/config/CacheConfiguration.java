package bg.hristomanov.education.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * Централизира инфраструктурните настройки за cache abstraction-а на Spring
 * и за refresh-ahead демонстрацията.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableScheduling
public class CacheConfiguration {

    public static final String SPRING_PRODUCTS_CACHE = "spring-products";

    /**
     * Caffeine е локален in-process cache: всяка application instance има
     * собствено независимо съдържание. Това е идеално за лабораторията,
     * но не решава distributed cache consistency (съгласуваност между няколко инстанции).
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SPRING_PRODUCTS_CACHE);
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(Duration.ofMinutes(1))
                        .recordStats()
        );
        return cacheManager;
    }
}
