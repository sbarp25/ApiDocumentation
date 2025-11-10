package com.apidoc.apidocumentation.doc;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiDocConfig implements WebMvcConfigurer {
    
    private final ApiLoggingInterceptor loggingInterceptor;
    
    public ApiDocConfig(ApiLoggingInterceptor loggingInterceptor) {
        this.loggingInterceptor = loggingInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**"); // Customize pattern as needed
    }
}