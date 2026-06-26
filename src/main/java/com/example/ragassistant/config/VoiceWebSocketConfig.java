package com.example.ragassistant.config;

import com.example.ragassistant.voice.VoiceWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class VoiceWebSocketConfig implements WebSocketConfigurer {

    private final VoiceWebSocketHandler voiceWebSocketHandler;

    public VoiceWebSocketConfig(VoiceWebSocketHandler voiceWebSocketHandler) {
        this.voiceWebSocketHandler = voiceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 로컬 PoC: same-origin이라 allowedOrigins는 넓게. 배포 시 도메인 제한 필요.
        registry.addHandler(voiceWebSocketHandler, "/ws/voice").setAllowedOriginPatterns("*");
    }

    // 오디오(MP3) 바이너리 프레임 대비 메시지 버퍼 상향
    @Bean
    ServletServerContainerFactoryBean createWebSocketContainer() {
        var container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024); // 1MB
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
    }
}
