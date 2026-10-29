package bg.hristomanov.education.boot4;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Версията е част от публичния HTTP contract, а не от името на Java метода.
 * Изискваме explicit header, за да не сменяме неусетно формата на legacy клиентите.
 */
@Configuration
public class ApiVersionConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("X-Dogs-Version");
    }
}
