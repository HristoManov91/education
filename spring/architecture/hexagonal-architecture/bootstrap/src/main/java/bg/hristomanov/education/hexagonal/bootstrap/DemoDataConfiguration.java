package bg.hristomanov.education.hexagonal.bootstrap;

import bg.hristomanov.education.hexagonal.adapter.out.jpa.JpaProductEntity;
import bg.hristomanov.education.hexagonal.adapter.out.jpa.SpringDataProductRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class DemoDataConfiguration {

    @Bean
    public ApplicationRunner seedHexagonalProducts(
            SpringDataProductRepository repository
    ) {
        return arguments -> {
            if (repository.count() > 0) {
                return;
            }

            repository.saveAll(
                    List.of(
                            new JpaProductEntity(
                                    "KEYBOARD",
                                    "Mechanical Keyboard",
                                    new BigDecimal("100.00")
                            ),
                            new JpaProductEntity(
                                    "MOUSE",
                                    "Wireless Mouse",
                                    new BigDecimal("40.00")
                            ),
                            new JpaProductEntity(
                                    "MONITOR",
                                    "27 inch Monitor",
                                    new BigDecimal("300.00")
                            )
                    )
            );
        };
    }
}
