package com.jms.spotifyplaylistauth.service;

import com.jms.spotifyplaylistauth.config.SpotifyConfig;
import com.jms.spotifyplaylistauth.dto.SpotifyTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Service
public class SpotifyService {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyService.class);

    private final WebClient webClient;
    private final SpotifyConfig spotifyConfig;

    @Autowired
    public SpotifyService(WebClient webClient, SpotifyConfig spotifyConfig) {
        this.webClient = webClient;
        this.spotifyConfig = spotifyConfig;
    }

    public String buildSpotifyAuthorizationUrl() {
        return buildSpotifyAuthorizationUrl(null);
    }
    
    public String buildSpotifyAuthorizationUrl(String state) {
        return buildSpotifyAuthorizationUrl(state, null);
    }
    
    public String buildSpotifyAuthorizationUrl(String state, String originalRedirectUrl) {
        // Generate a random CSRF token for security
        String csrfToken = UUID.randomUUID().toString();
        
        // Create a state object that includes both the CSRF token and the original redirect URL
        String effectiveState;
        
        if (originalRedirectUrl != null && !originalRedirectUrl.isEmpty()) {
            // Create a JSON object with the CSRF token and redirect URL
            try {
                String stateJson = String.format("{\"csrf\":\"%s\",\"redirectTo\":\"%s\"}", 
                    csrfToken, originalRedirectUrl);
                // Base64 encode the JSON
                effectiveState = java.util.Base64.getEncoder().encodeToString(stateJson.getBytes());
                logger.info("Created state with embedded redirect URL: {}", originalRedirectUrl);
            } catch (Exception e) {
                logger.error("Error creating state with redirect URL: {}", e.getMessage());
                effectiveState = csrfToken;
            }
        } else {
            // If no original URL provided, just use the state provided or the CSRF token
            effectiveState = (state != null && !state.isEmpty()) ? state : csrfToken;
        }
        
        // Select the appropriate redirect URI based on the state
        String redirectUri = spotifyConfig.getRedirectUriForState(effectiveState);
        
        // Build the Spotify authorization URL
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://accounts.spotify.com/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", spotifyConfig.getClientId())
                .queryParam("scope", "user-read-private user-read-email playlist-read-private playlist-modify-private playlist-modify-public user-read-recently-played")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("show_dialog", "true")
                .queryParam("state", effectiveState);
        
        String authUrl = builder.build().toUriString();
                
        logger.info("Built authorization URL with client ID: {}", spotifyConfig.getClientId());
        logger.info("Using redirect URI: {}", redirectUri);
        logger.info("Using state: {} (original URL encoded within: {})", effectiveState, originalRedirectUrl);
        
        // Log the full URL to help with debugging
        logger.debug("Full authorization URL: {}", authUrl);
        
        return authUrl;
    }

    /**
     * Exchange the authorization code for an access token
     */
    public SpotifyTokenResponse exchangeCodeForToken(String code) {
        return exchangeCodeForToken(code, null);
    }
    
    /**
     * Exchange the authorization code for an access token with state
     */
    public SpotifyTokenResponse exchangeCodeForToken(String code, String state) {
        try {
            String credentials = spotifyConfig.getClientId() + ":" + spotifyConfig.getClientSecret();
            String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
            
            // Select the appropriate redirect URI based on the state
            String redirectUri = spotifyConfig.getRedirectUriForState(state);
            
            logger.info("Exchanging code for token directly with Spotify using redirect URI: {}", redirectUri);
            logger.info("Using state: {}", state);
            
            Map<String, Object> response = webClient.post()
                    .uri("https://accounts.spotify.com/api/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Basic " + base64Credentials)
                    .bodyValue(
                        "grant_type=authorization_code" +
                        "&code=" + code +
                        "&redirect_uri=" + redirectUri
                    )
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response == null || !response.containsKey("access_token")) {
                logger.error("Invalid response from Spotify: {}", response);
                throw new RuntimeException("Invalid response from Spotify: " + response);
            }
            
            String accessToken = (String) response.get("access_token");
            logger.info("Successfully obtained access token from Spotify (length: {})", accessToken.length());
            
            SpotifyTokenResponse tokenResponse = new SpotifyTokenResponse();
            tokenResponse.setAccessToken(accessToken);
            tokenResponse.setTokenType((String) response.get("token_type"));
            tokenResponse.setExpiresIn((Integer) response.get("expires_in"));
            tokenResponse.setRefreshToken((String) response.get("refresh_token"));
            tokenResponse.setScope((String) response.get("scope"));
            tokenResponse.setState(state);
            
            // Verify the token works by making a test API call
            try {
                Map<String, Object> userProfile = getUserProfile(accessToken);
                if (userProfile != null && userProfile.containsKey("id")) {
                    logger.info("Token verified - successfully retrieved user profile for: {}", userProfile.get("id"));
                } else {
                    logger.warn("Token may not be valid - could not retrieve user profile");
                }
            } catch (Exception e) {
                logger.warn("Error verifying token with user profile call: {}", e.getMessage());
                // Don't throw here - the token might still work for other operations
            }
            
            return tokenResponse;
        } catch (WebClientResponseException e) {
            logger.error("WebClientResponseException: {}", e.getMessage(), e);
            logger.error("Response body: {}", e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Exception exchanging code for token: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Map<String, Object> getUserProfile(String accessToken) {
        return webClient.get()
                .uri("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
