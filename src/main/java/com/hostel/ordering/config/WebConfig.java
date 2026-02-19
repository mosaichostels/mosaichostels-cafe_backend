package com.hostel.ordering.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        String corsOrigins = System.getenv()
                .getOrDefault("CORS_ALLOWED_ORIGINS", "");

        if (corsOrigins.equals("*")) {
            registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false);
        } else {
            String[] allowedOrigins = corsOrigins.isEmpty()
                    ? new String[]{"*"}
                    : corsOrigins.split(",");
            registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false);
        }
    }
}
