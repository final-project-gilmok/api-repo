package kr.gilmok.api.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    // ✅ 기존 RedisTemplate 빈 그대로 유지
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    // ✅ 새로 추가: TCP Keepalive + 빠른 실패 설정
    // ConnectionFactory는 건드리지 않고 옵션만 커스터마이징
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> builder.clientOptions(
                ClientOptions.builder()
                        .socketOptions(
                                SocketOptions.builder()
                                        .tcpNoDelay(true)
                                        .keepAlive(                         // 죽은 연결 조기 감지
                                                SocketOptions.KeepAliveOptions.builder()
                                                        .enable(true)
                                                        .idle(Duration.ofSeconds(30))
                                                        .interval(Duration.ofSeconds(10))
                                                        .count(3)
                                                        .build()
                                        )
                                        .connectTimeout(Duration.ofMillis(2000))
                                        .build()
                        )
                        .disconnectedBehavior(
                                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS  // 끊긴 연결에서 즉시 실패
                        )
                        .autoReconnect(true)
                        .build()
        );
    }

    @SuppressWarnings("unchecked")
    @Bean
    public DefaultRedisScript<List<Object>> fastAdmissionCycleScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/fast-admission-cycle.lua")));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> seatLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/seat-lock.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> seatUnlockRestoreScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/seat-unlock-restore.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @SuppressWarnings("unchecked")
    @Bean
    public DefaultRedisScript<List<Long>> queueStatusScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/queue-status.lua")));
        script.setResultType(List.class);
        return script;
    }

    @SuppressWarnings("unchecked")
    @Bean
    public DefaultRedisScript<List<Object>> registerIdempotentScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/register-idempotent.lua")));
        script.setResultType(List.class);
        return script;
    }

    @SuppressWarnings("unchecked")
    @Bean
    public DefaultRedisScript<List<Long>> admissionRateScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/admission-rate.lua")));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate-limit.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> unlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/unlock.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> removeAdmittedUserScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/remove-admitted-user.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> policyEnforceScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/policy-enforce.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
