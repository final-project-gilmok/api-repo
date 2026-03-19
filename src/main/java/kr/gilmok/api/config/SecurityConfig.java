package kr.gilmok.api.config;

import jakarta.servlet.Filter;
import kr.gilmok.api.policy.filter.PolicyFilter;
import kr.gilmok.common.security.AccessTokenBlocklistFilter;
import kr.gilmok.common.security.CommonSecurityConfig;
import kr.gilmok.common.filter.JwtAuthenticationFilter;
import kr.gilmok.common.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends CommonSecurityConfig {

    private final PolicyFilter policyFilter;
    private final AccessTokenBlocklistFilter accessTokenBlocklistFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
            PolicyFilter policyFilter,
            AccessTokenBlocklistFilter accessTokenBlocklistFilter) {
        super(jwtAuthenticationFilter, customAuthenticationEntryPoint);
        this.policyFilter = policyFilter;
        this.accessTokenBlocklistFilter = accessTokenBlocklistFilter;
    }

    @Override
    protected List<Filter> getFiltersAfterJwtAuthentication() {
        return List.of(accessTokenBlocklistFilter, policyFilter);
    }

    @Override
    protected void configureRequestMatchers(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/events/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/error").permitAll();
    }
}
