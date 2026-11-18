package bg.hristomanov.education.quarkus.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Full Quarkus augmentation + generated CDI bean + HTTP check.
 * Java unit tests alone cannot prove the extension wiring.
 */
@QuarkusTest
class GreetingResourceTest {
    @Test
    void generatedBeanIsInjectedAndServedOverHttp() {
        given().when().get("/generated-greeting")
                .then().statusCode(200)
                .body(equalTo("Hello from build-time augmentation"));
    }
}
