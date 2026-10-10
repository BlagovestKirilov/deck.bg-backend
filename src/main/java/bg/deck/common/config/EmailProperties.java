package bg.deck.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The mail server, read once at startup and never written to.
 *
 * <p>Registered by {@link Config}, not by an annotation of its own: a record is
 * final, and Spring cannot proxy a final {@code @Configuration} class.
 *
 * @param host     the SMTP host
 * @param port     its port
 * @param username the account the mail is sent from
 * @param password that account's password
 */
@ConfigurationProperties(prefix = "spring.mail")
public record EmailProperties(String host, Integer port, String username, String password) {

    public static final String SMTP_AUTH = "mail.smtp.auth";
    public static final String STARTTLS_ENABLE = "mail.smtp.starttls.enable";
}
