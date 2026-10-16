package com.helpdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled React build (classpath:/static) when the app is packaged with
 * the frontend, so one jar on one port serves both UI and API. Unknown non-API paths
 * fall back to index.html so client-side routes like /tickets/12 survive a reload.
 * In local development nothing is bundled and this handler simply finds nothing.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // API and docs paths must 404 as JSON, never return the UI
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("v3/")
                                || resourcePath.startsWith("swagger-ui")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}
