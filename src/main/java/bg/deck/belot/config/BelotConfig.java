package bg.deck.belot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers belot's timings. A class, because Spring proxies this one. */
@Configuration
@EnableConfigurationProperties(BelotProperties.class)
public class BelotConfig {
}
