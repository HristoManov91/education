package bg.hristomanov.education.loom.bankapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot entry point (входната точка за стартиране) за bank-api модула.
 *
 * <p>Лабораторията нарочно използва blocking {@link RestClient}
 * (HTTP клиент, който изчаква отговора), защото искаме да покажем основната Virtual Threads идея:
 * можем да запазим simple synchronous request/response code (прост последователен код заявка/отговор),
 * без всеки чакащ HTTP call да държи отделен тежък OS thread през целия wait (период на чакане).</p>
 */
@SpringBootApplication
public class BankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApiApplication.class, args);
    }

    /**
     * Един shared RestClient configuration bean (споделена Spring конфигурация за HTTP клиента)
     * с base URL към dummy downstream приложението
     * (учебната услуга, която текущото приложение извиква).
     *
     * <p>Самият RestClient НЕ е „Loom API“. Той просто ни дава реалистичен blocking I/O workload
     * (натоварване, при което значителна част от времето се чака I/O), върху който да видим
     * ползата от virtual threads и structured fan-out
     * (структурирано разклоняване към няколко независими операции).</p>
     */
    @Bean
    RestClient bankServicesRestClient(
            RestClient.Builder builder,
            @Value("${bank-services.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
