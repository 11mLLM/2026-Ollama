package com.krbrief.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedMethods("GET", "POST", "PUT", "OPTIONS")
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
        .allowedHeaders("*");
  }
}
