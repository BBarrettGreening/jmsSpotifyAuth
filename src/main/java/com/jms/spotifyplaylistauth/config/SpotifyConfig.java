package com.jms.spotifyplaylistauth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;

@Configuration
public class SpotifyConfig {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyConfig.class);

    @Value("${spotify.client-id}")
    private String clientId;

    @Value("${spotify.client-secret}")
    private String clientSecret;

    @Value("${spotify.redirect-uri}")
    private String redirectUri;

    @Value("${spotify.jms.callback-url}")
    private String jmsCallbackUrl;

    @PostConstruct
    public void init() {
        if (clientId == null || clientId.equals("${SPOTIFY_CLIENT_ID}") || clientId.isEmpty()) {
            logger.warn("SPOTIFY_CLIENT_ID environment variable is not set! Authentication will fail.");
            logger.info("Please set the SPOTIFY_CLIENT_ID environment variable with your Spotify Client ID.");
        }
        
        if (clientSecret == null || clientSecret.equals("${SPOTIFY_CLIENT_SECRET}") || clientSecret.isEmpty()) {
            logger.warn("SPOTIFY_CLIENT_SECRET environment variable is not set! Authentication will fail.");
            logger.info("Please set the SPOTIFY_CLIENT_SECRET environment variable with your Spotify Client Secret.");
        }
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
    
    /**
     * Gets the appropriate redirect URI based on the provided state
     * For compatibility, this method now simply returns the main redirect URI
     * 
     * @param state A string indicating which redirect URI to use
     * @return The redirect URI
     */
    public String getRedirectUriForState(String state) {
        return redirectUri;
    }

    public String getJmsCallbackUrl() {
        return jmsCallbackUrl;
    }
}