package bg.hristomanov.education.loom.bankapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot entry point за bank-api модула.
 *
 * <p>Лабораторията нарочно използва blocking {@link RestClient}, защото искаме да покажем
 * основната Virtual Threads идея: можем да запазим simple synchronous request/response code,
 * без всеки чакащ HTTP call да държи отделен тежък OS thread през целия wait.</p>
 */
@SpringBootApplication
public class BankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApiApplication.class, args);
    }

    /**
     * Един shared RestClient configuration bean с base URL към dummy downstream приложението.
     *
     * <p>Самият RestClient НЕ е „Loom API“. Той просто ни дава реалистичен blocking I/O
     * workload, върху който да видим ползата от virtual threads и structured fan-out.</p>
     */
    @Bean
    RestClient bankServicesRestClient(
            RestClient.Builder builder,
            @Value("${bank-services.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
