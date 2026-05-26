package com.nauta.triage.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    private final BearerAuthInterceptor interceptor;

    public SecurityConfig(BearerAuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/containers/**")
                .addPathPatterns("/decisions/**");
        // /webhooks/** uses HMAC, not bearer — registered in Phase 4.
    }
}
