package kr.gilmok.api.config;

import kr.gilmok.api.queue.interceptor.QueueRateLimitInterceptor;
import kr.gilmok.api.token.interceptor.AdmissionTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdmissionTokenInterceptor admissionTokenInterceptor;

    @Autowired(required = false)
    private QueueRateLimitInterceptor queueRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (queueRateLimitInterceptor != null) {
            registry.addInterceptor(queueRateLimitInterceptor)
                    .addPathPatterns("/queue/**");
        }

        registry.addInterceptor(admissionTokenInterceptor)
                .addPathPatterns("/reservations/*/confirm");
    }
}
