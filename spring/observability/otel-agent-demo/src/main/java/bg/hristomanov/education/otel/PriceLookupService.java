package bg.hristomanov.education.otel;

import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Същият бизнес поток работи с и без агент. Незадължителният Redis cache създава
 * допълнителен span и видимо различен trace path при cache hit/miss.
 */
@Service
public final class PriceLookupService {

    private final RestClient pricesClient;
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;

    public PriceLookupService(RestClient.Builder builder, StringRedisTemplate redis,
            @Value("${lab.price-base-url:http://127.0.0.1:8086}") String upstreamUrl,
            @Value("${lab.redis.enabled:false}") boolean redisEnabled) {
        this.pricesClient = builder.baseUrl(upstreamUrl).build();
        this.redis = redis;
        this.redisEnabled = redisEnabled;
    }

    public QuoteDto quote(String sku) {
        String cacheKey = "education:otel:price:" + sku;
        if (redisEnabled) {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                long cents = Long.parseLong(cached);
                return new QuoteDto(sku, Math.multiplyExact(2L, cents), true);
            }
        }

        PriceDto price = Objects.requireNonNull(pricesClient.get()
                .uri("/internal/prices/{sku}", sku)
                .retrieve().body(PriceDto.class), "Upstream returned no price");

        if (redisEnabled) {
            redis.opsForValue().set(cacheKey,
                    Long.toString(price.unitPriceCents()), Duration.ofSeconds(30));
        }
        return new QuoteDto(sku, Math.multiplyExact(2L, price.unitPriceCents()), false);
    }
}
