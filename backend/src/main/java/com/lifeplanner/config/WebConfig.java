package com.lifeplanner.config;

import com.lifeplanner.util.ApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS restricted to the configured local dev origins and the v1 API only. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PlannerProperties properties;

    public WebConfig(PlannerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(ApiPaths.V1 + "/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("Content-Type", "Accept", "Authorization")
                .exposedHeaders("X-Total-Count", "WWW-Authenticate", "Retry-After")
                // the refresh-token cookie must travel with cross-origin dev requests (origins are an explicit list)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
