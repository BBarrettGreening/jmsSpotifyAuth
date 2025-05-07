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
        // Always use the main redirect URI for all scenarios
        String redirectUri = spotifyConfig.getRedirectUri();
        
        // Build the Spotify authorization URL
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://accounts.spotify.com/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", spotifyConfig.getClientId())
                .queryParam("scope", "user-read-private user-read-email playlist-read-private playlist-modify-private playlist-modify-public")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("show_dialog", "true");
        
        // Add state if provided
        if (state != null && !state.isEmpty()) {
            builder.queryParam("state", state);
        }
        
        String authUrl = builder.build().toUriString();
                
        logger.info("Built authorization URL with client ID: {}", spotifyConfig.getClientId());
        logger.info("Using redirect URI: {}", redirectUri);
        
        return authUrl;
    }

    public SpotifyTokenResponse exchangeCodeForToken(String code) {
        return exchangeCodeForToken(code, null);
    }
    
    public SpotifyTokenResponse exchangeCodeForToken(String code, String state) {
        try {
            String credentials = spotifyConfig.getClientId() + ":" + spotifyConfig.getClientSecret();
            String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
            
            // Always use the main redirect URI for all scenarios
            String redirectUri = spotifyConfig.getRedirectUri();
            
            logger.info("Exchanging code for token directly with Spotify using redirect URI: {}", redirectUri);
            
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
                throw new RuntimeException("Invalid response from Spotify: " + response);
            }
            
            SpotifyTokenResponse tokenResponse = new SpotifyTokenResponse();
            tokenResponse.setAccessToken((String) response.get("access_token"));
            tokenResponse.setTokenType((String) response.get("token_type"));
            tokenResponse.setExpiresIn((Integer) response.get("expires_in"));
            tokenResponse.setRefreshToken((String) response.get("refresh_token"));
            tokenResponse.setScope((String) response.get("scope"));
            tokenResponse.setState(state);
            
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
