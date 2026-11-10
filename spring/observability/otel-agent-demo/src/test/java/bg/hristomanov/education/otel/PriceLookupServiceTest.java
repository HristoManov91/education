package bg.hristomanov.education.otel;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceLookupServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void redisHitSkipsDownstreamHttp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("education:otel:price:widget")).thenReturn("1250");

        // Port 1 is intentionally unavailable: any unexpected outbound HTTP would fail.
        PriceLookupService service = new PriceLookupService(
                RestClient.builder(), redis, "http://127.0.0.1:1", true);

        QuoteDto response = service.quote("widget");
        assertEquals(2500L, response.totalCents());
        assertTrue(response.cached());
        verify(operations).get("education:otel:price:widget");
    }
}
