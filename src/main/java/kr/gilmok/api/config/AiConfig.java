package kr.gilmok.api.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.context.annotation.Profile("!test")
public class AiConfig {

    // Spring AI가 내부적으로 만들어둔 Builder를 가져와서, 우리가 쓸 ChatClient를 조립해서 Bean으로 등록해 줍니다.
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}