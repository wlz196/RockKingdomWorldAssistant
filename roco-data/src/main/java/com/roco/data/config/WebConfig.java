package com.roco.data.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:media/");
        // 图鉴前端静态文件（构建产物）
        registry.addResourceHandler("/**")
                .addResourceLocations("file:frontend-data/dist/", "file:frontend-data/");
    }
}
