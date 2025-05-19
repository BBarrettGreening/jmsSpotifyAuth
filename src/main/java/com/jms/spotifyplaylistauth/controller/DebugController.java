package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.config.SpotifyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for debug endpoints - only available in dev and test profiles
 */
@Controller
@RequestMapping("/debug")
@Profile({"dev", "test"})
public class DebugController {
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);
    
    private final SpotifyConfig spotifyConfig;
    
    @Value("${jms.website.playlists-url}")
    private String jmsPlaylistsUrl;
    
    @Value("${jms.api.playlists-url}")
    private String jmsApiPlaylistsUrl;
    
    @Value("${jms.api.use-api-first}")
    private boolean useApiFirst;
    
    @Autowired
    public DebugController(SpotifyConfig spotifyConfig) {
        this.spotifyConfig = spotifyConfig;
    }
    
    @GetMapping("/spotify-config")
    public String showSpotifyConfig(Model model) {
        Map<String, String> config = new HashMap<>();
        
        // Spotify configuration
        config.put("Client ID", maskString(spotifyConfig.getClientId()));
        config.put("Client Secret", maskString(spotifyConfig.getClientSecret()));
        config.put("Redirect URI", spotifyConfig.getRedirectUri());
        config.put("JMS Callback URL", spotifyConfig.getJmsCallbackUrl());
        
        // JMS website configuration
        config.put("JMS Playlists URL", jmsPlaylistsUrl);
        config.put("JMS API Playlists URL", jmsApiPlaylistsUrl);
        config.put("Use API First", String.valueOf(useApiFirst));
        
        // Active profiles
        config.put("Active Profiles", String.join(", ", System.getProperty("spring.profiles.active", "default")));
        
        model.addAttribute("config", config);
        return "debug-config";
    }
    
    @GetMapping("/cleanup-playlists")
    public String showCleanupPlaylists(
            @RequestParam(name = "accessToken", required = false) String accessToken,
            Model model) {
        logger.info("Showing playlist cleanup page");
        
        if (accessToken != null && !accessToken.isEmpty()) {
            model.addAttribute("accessToken", accessToken);
            logger.info("Access token provided for cleanup page");
        }
        
        return "cleanup-playlists";
    }
    
    /**
     * Mask sensitive strings to show only first and last few characters
     */
    private String maskString(String value) {
        if (value == null || value.isEmpty()) {
            return "[EMPTY]";
        }
        
        if (value.length() <= 8) {
            return "****";
        }
        
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}