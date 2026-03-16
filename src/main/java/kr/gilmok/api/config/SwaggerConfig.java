package kr.gilmok.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {

    private final ObjectMapper objectMapper;

    @Value("${auth.api-docs-url:}")
    private String authApiDocsUrl;

    @Bean
    public OpenAPI apiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gilmok API")
                        .description("길목 관리자/사용자 API 문서")
                        .version("v1")
                        .contact(new Contact().name("Gilmok Team")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/users/**", "/reservations/**", "/queue/**", "/events/**")
                .build();
    }

    /** auth-repo OpenAPI 스펙을 가져와 병합 → default/public/admin 모든 그룹에 Auth 표시 */
    @Bean
    @Order(0)
    public GlobalOpenApiCustomizer mergeAuthOpenAPICustomizer() {
        return openApi -> {
            if (authApiDocsUrl == null || authApiDocsUrl.isBlank()) {
                return;
            }
            try {
                ResponseEntity<Map> response = new RestTemplate().getForEntity(authApiDocsUrl, Map.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    return;
                }
                Map<String, Object> authSpec = response.getBody();
                @SuppressWarnings("unchecked")
                Map<String, Object> paths = (Map<String, Object>) authSpec.get("paths");
                if (paths != null && !paths.isEmpty()) {
                    Paths merged = openApi.getPaths() != null ? openApi.getPaths() : new Paths();
                    paths.forEach((path, pathItem) -> merged.put(path, objectMapper.convertValue(pathItem, io.swagger.v3.oas.models.PathItem.class)));
                    openApi.setPaths(merged);
                    log.info("Swagger: auth-repo 스펙 병합 완료 (paths {} 개)", paths.size());
                }
            } catch (Exception e) {
                log.debug("Swagger: auth-repo 스펙 병합 스킵 (auth.api-docs-url 미연결 등): {}", e.getMessage());
            }
        };
    }
}

