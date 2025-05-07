package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.config.SpotifyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
public class SpotifyDebugController {

    private final SpotifyConfig spotifyConfig;

    @Autowired
    public SpotifyDebugController(SpotifyConfig spotifyConfig) {
        this.spotifyConfig = spotifyConfig;
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
}
