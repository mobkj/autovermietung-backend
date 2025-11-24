package com.autovermietung.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // liefert alles aus /uploads/** statisch aus dem Ordner uploads/ auf dem Server
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
