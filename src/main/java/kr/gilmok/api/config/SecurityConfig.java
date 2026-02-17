package kr.gilmok.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/queue/**").permitAll()
                        .requestMatchers("/events/**").permitAll()
                        .requestMatchers("/reservations/**").permitAll()
                        .requestMatchers("/admin/**").permitAll() // TODO: 인증 구현 후 hasRole("ADMIN")으로 복원
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
