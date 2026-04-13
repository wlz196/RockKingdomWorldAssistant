package com.roco.data.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${roco.media-path:media/}")
    private String mediaPath;

    @Value("${roco.frontend-path:frontend-data/dist/}")
    private String frontendPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 确保路径以 / 结尾
        String media = mediaPath.endsWith("/") ? mediaPath : mediaPath + "/";
        String frontend = frontendPath.endsWith("/") ? frontendPath : frontendPath + "/";

        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:" + media);
        // 图鉴前端静态文件（构建产物）
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + frontend);
    }
}
