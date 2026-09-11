package bg.hristomanov.education.loom.bankapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class BankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApiApplication.class, args);
    }

    @Bean
    RestClient bankServicesRestClient(
            RestClient.Builder builder,
            @Value("${bank-services.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
