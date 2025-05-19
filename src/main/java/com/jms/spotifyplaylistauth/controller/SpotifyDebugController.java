package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.config.SpotifyConfig;
import com.jms.spotifyplaylistauth.service.SpotifyPlaylistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
public class SpotifyDebugController {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyDebugController.class);

    private final SpotifyConfig spotifyConfig;
    private final SpotifyPlaylistService spotifyPlaylistService;

    @Autowired
    public SpotifyDebugController(SpotifyConfig spotifyConfig, SpotifyPlaylistService spotifyPlaylistService) {
        this.spotifyConfig = spotifyConfig;
        this.spotifyPlaylistService = spotifyPlaylistService;
    }

    @GetMapping("/spotify-config")
    public Map<String, String> getSpotifyConfig() {
        Map<String, String> config = new HashMap<>();
        
        // Only show a masked version of the client secret for security
        String maskedSecret = "";
        if (spotifyConfig.getClientSecret() != null && !spotifyConfig.getClientSecret().isEmpty()) {
            if (spotifyConfig.getClientSecret().length() > 4) {
                maskedSecret = spotifyConfig.getClientSecret().substring(0, 4) + "..." + 
                               spotifyConfig.getClientSecret().substring(spotifyConfig.getClientSecret().length() - 4);
            } else {
                maskedSecret = "****";
            }
        }
        
        config.put("clientId", spotifyConfig.getClientId());
        config.put("clientSecret", maskedSecret);
        config.put("redirectUri", spotifyConfig.getRedirectUri());
        config.put("jmsCallbackUrl", spotifyConfig.getJmsCallbackUrl());
        
        return config;
    }
    
    /**
     * Delete all playlists that match a specific pattern
     */
    @PostMapping("/cleanup-playlists")
    public Map<String, Object> cleanupPlaylists(
            @RequestParam String accessToken,
            @RequestParam(defaultValue = "JMS \\d{2}\\.\\d{2}\\.\\d{2}") String namePattern) {
        
        logger.info("Starting cleanup of playlists matching pattern: {}", namePattern);
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Validate the access token by getting the user profile
            Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
            
            if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
                logger.error("Invalid access token for playlist cleanup");
                result.put("error", "Invalid access token. Please authenticate with Spotify.");
                return result;
            }
            
            // Delete the playlists
            int deletedCount = spotifyPlaylistService.deletePlaylists(accessToken, namePattern);
            
            logger.info("Successfully deleted {} playlists matching pattern: {}", deletedCount, namePattern);
            result.put("success", true);
            result.put("deletedCount", deletedCount);
            result.put("message", String.format("Successfully deleted %d playlists matching pattern: %s", deletedCount, namePattern));
            
        } catch (Exception e) {
            logger.error("Error cleaning up playlists: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Error cleaning up playlists: " + e.getMessage());
        }
        
        return result;
    }
}