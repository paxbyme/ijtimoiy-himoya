package com.manager.config;

import com.manager.websocket.LiveAudioWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveAudioWebSocketHandler liveAudioWebSocketHandler;

    public WebSocketConfig(LiveAudioWebSocketHandler liveAudioWebSocketHandler) {
        this.liveAudioWebSocketHandler = liveAudioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(liveAudioWebSocketHandler, "/api/ai/live")
                .setAllowedOriginPatterns("*");
    }

    /** Allow larger binary frames for audio chunks (raw PCM ~32KB at 16kHz/1s). */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(256 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(10L * 60L * 1000L); // 10 min
        return container;
    }
}
