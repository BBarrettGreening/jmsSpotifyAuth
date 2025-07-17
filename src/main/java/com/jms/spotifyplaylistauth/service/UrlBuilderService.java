package com.jms.spotifyplaylistauth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for building URLs that work correctly regardless of where the app is hosted
 */
@Service
public class UrlBuilderService {
    private static final Logger logger = LoggerFactory.getLogger(UrlBuilderService.class);
    
    @Value("${app.base-url:}")
    private String configuredBaseUrl;
    
    /**
     * Build a full URL for a given path, using either configured base URL or dynamic detection
     * 
     * @param request The HTTP request (for dynamic URL building)
     * @param path The path to append (e.g., "/whatsapp/upload")
     * @return The full URL
     */
    public String buildFullUrl(HttpServletRequest request, String path) {
        // If a base URL is configured, use it
        if (configuredBaseUrl != null && !configuredBaseUrl.isEmpty()) {
            logger.debug("Using configured base URL: {}", configuredBaseUrl);
            return configuredBaseUrl + path;
        }
        
        // Otherwise, build it dynamically from the request
        return buildDynamicUrl(request, path);
    }
    
    /**
     * Build a URL dynamically based on the current request
     * 
     * @param request The HTTP request
     * @param path The path to append
     * @return The full URL
     */
    private String buildDynamicUrl(HttpServletRequest request, String path) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        // Check for X-Forwarded headers (when behind a proxy)
        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        String xForwardedHost = request.getHeader("X-Forwarded-Host");
        String xForwardedPort = request.getHeader("X-Forwarded-Port");
        
        // Use forwarded values if available
        if (xForwardedProto != null) {
            scheme = xForwardedProto;
        }
        if (xForwardedHost != null) {
            serverName = xForwardedHost;
            // X-Forwarded-Host might include port
            if (serverName.contains(":")) {
                String[] parts = serverName.split(":");
                serverName = parts[0];
                if (xForwardedPort == null) {
                    xForwardedPort = parts[1];
                }
            }
        }
        if (xForwardedPort != null) {
            try {
                serverPort = Integer.parseInt(xForwardedPort);
            } catch (NumberFormatException e) {
                logger.warn("Invalid X-Forwarded-Port: {}", xForwardedPort);
            }
        }
        
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(scheme).append("://").append(serverName);
        
        // Only add port if it's not the default for the scheme
        boolean isDefaultPort = ("http".equals(scheme) && serverPort == 80) || 
                               ("https".equals(scheme) && serverPort == 443);
        
        if (!isDefaultPort && xForwardedHost != null && !xForwardedHost.contains(":")) {
            // Only add port if X-Forwarded-Host doesn't already include it
            urlBuilder.append(":").append(serverPort);
        } else if (!isDefaultPort && xForwardedHost == null) {
            // Add port if not using forwarded host and not default
            urlBuilder.append(":").append(serverPort);
        }
        
        urlBuilder.append(contextPath).append(path);
        
        String fullUrl = urlBuilder.toString();
        logger.debug("Built dynamic URL: {}", fullUrl);
        
        return fullUrl;
    }
    
    /**
     * Extract the redirect URL from a Base64-encoded state parameter
     * 
     * @param state The state parameter
     * @return The redirect URL, or null if not found
     */
    public String extractRedirectUrlFromState(String state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        
        try {
            // Try to decode the state as Base64 JSON
            String decodedState = new String(java.util.Base64.getDecoder().decode(state));
            if (decodedState.contains("redirectTo")) {
                // Extract the redirect URL from the JSON
                int startIndex = decodedState.indexOf("\"redirectTo\":\"") + 14;
                int endIndex = decodedState.indexOf("\"", startIndex);
                if (startIndex > 13 && endIndex > startIndex) {
                    String redirectUrl = decodedState.substring(startIndex, endIndex);
                    logger.debug("Extracted redirect URL from state: {}", redirectUrl);
                    return redirectUrl;
                }
            }
        } catch (Exception e) {
            logger.debug("State is not Base64 encoded JSON: {}", e.getMessage());
        }
        
        return null;
    }
}
