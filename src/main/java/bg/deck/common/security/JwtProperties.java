package bg.deck.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
@Configuration
public class JwtProperties {
    private String secretKey;
    private long expiration;
    private long refreshExpiration;
}
