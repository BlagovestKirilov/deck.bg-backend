package bg.deck.common.config;

import bg.deck.common.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import static bg.deck.common.constant.Constants.APP;
import static bg.deck.common.constant.Constants.DECK_BG;
import static bg.deck.common.constant.Constants.LOCALHOST;
import static bg.deck.common.constant.Constants.PROD;
import static bg.deck.common.constant.Constants.TOPIC;
import static bg.deck.common.constant.Constants.WEB_SOCKET_ENDPOINT;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    /**
     * Who the socket belongs to is decided on the CONNECT frame, not on the
     * handshake — that is what keeps the token out of the URL, and so out of
     * the logs. The same interceptor checks that a player subscribes only to
     * their own topics.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 1. Prefix for messages sent *to* the client from the server (the push updates)
        config.enableSimpleBroker(TOPIC);

        // 2. Prefix for messages sent *from* the client *to* the server (e.g., player moves)
        config.setApplicationDestinationPrefixes(APP);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(WEB_SOCKET_ENDPOINT)
                .setAllowedOriginPatterns(activeProfile.equals(PROD) ? DECK_BG : LOCALHOST)
                .withSockJS();
    }
}
