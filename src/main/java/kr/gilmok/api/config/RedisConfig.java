package kr.gilmok.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Configuration
public class RedisConfig {

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
}
