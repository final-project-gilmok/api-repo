package kr.gilmok.api.config;

import kr.gilmok.common.config.CommonSecurityConfig;
import kr.gilmok.common.filter.JwtAuthenticationFilter;
import kr.gilmok.common.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends CommonSecurityConfig {

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomAuthenticationEntryPoint customAuthenticationEntryPoint) {
        super(jwtAuthenticationFilter, customAuthenticationEntryPoint);
    }

    @Override
    protected void configureRequestMatchers(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/queue/**").permitAll()
                .requestMatchers("/events/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
    }
}
