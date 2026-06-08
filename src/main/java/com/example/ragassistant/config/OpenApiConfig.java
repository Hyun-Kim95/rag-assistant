package com.example.ragassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI ragAssistantOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG Assistant API")
                        .description("로컬 Ollama + pgvector 문서 Q&A API")
                        .version("v0.1.0"));
    }
}
