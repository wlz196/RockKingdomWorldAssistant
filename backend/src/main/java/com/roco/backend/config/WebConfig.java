package com.roco.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Image resource mapping for Roco Encyclopedia artifacts.
 * Maps URL /media/** to the physical directory /root/roco/media/
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Use relative paths to avoid absolute path mapping issues on different OS/environments
        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:media/");

        // Map the root and other paths to the frontend directory
        registry.addResourceHandler("/**")
                .addResourceLocations("file:frontend/");
    }
}
