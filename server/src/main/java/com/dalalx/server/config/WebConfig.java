package com.dalalx.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Vite dev server (localhost:5173, or this computer's Wi-Fi address
 * for audience phones) to call the REST API directly.
 *
 * <p>Normally the Vite proxy makes this unnecessary (the browser sees one
 * origin), but CORS keeps direct calls working too, for example from tools or
 * a production build served elsewhere.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Pages allowed to call the API and open the WebSocket: this computer, plus
     * private network addresses (phones on the same Wi-Fi or hotspot). Nothing
     * on the public internet.
     */
    static final String[] ALLOWED_ORIGINS = {
        "http://localhost:*", "http://127.0.0.1:*",
        "http://192.168.*.*:*", "http://10.*.*.*:*", "http://172.*.*.*:*"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns(ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
