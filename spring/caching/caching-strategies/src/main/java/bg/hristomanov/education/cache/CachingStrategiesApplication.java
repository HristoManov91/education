package bg.hristomanov.education.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point за caching laboratory-то.
 *
 * <p>Нарочно не слагаме {@code @EnableCaching} тук. Така caching инфраструктурата
 * остава отделна concern (отговорност) и тестове, които не я искат, не са
 * принудени да я активират чрез main application class-а.</p>
 */
@SpringBootApplication
public class CachingStrategiesApplication {

    public static void main(String[] args) {
        SpringApplication.run(CachingStrategiesApplication.class, args);
    }
}
