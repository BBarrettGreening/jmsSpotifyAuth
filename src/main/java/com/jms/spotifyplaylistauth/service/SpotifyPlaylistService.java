package com.jms.spotifyplaylistauth.service;

import com.jms.spotifyplaylistauth.config.SpotifyConfig;
import com.jms.spotifyplaylistauth.dto.FridayPlaylist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
public class SpotifyPlaylistService {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyPlaylistService.class);
    
    private final WebClient webClient;
    
    @Autowired
    public SpotifyPlaylistService(WebClient webClient) {
        this.webClient = webClient;
    }
    
    /**
     * Get all playlists for the authenticated user
     */
    public List<Map<String, Object>> getUserPlaylists(String accessToken) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("https://api.spotify.com/v1/me/playlists?limit=50")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("items")) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                logger.info("Retrieved {} playlists for user", items.size());
                return items;
            }
            
            return Collections.emptyList();
        } catch (WebClientResponseException e) {
            logger.error("Error retrieving user playlists: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get the names of all playlists for the authenticated user
     */
    public Set<String> getUserPlaylistNames(String accessToken) {
        List<Map<String, Object>> playlists = getUserPlaylists(accessToken);
        Set<String> playlistNames = new HashSet<>();
        
        for (Map<String, Object> playlist : playlists) {
            if (playlist.containsKey("name")) {
                playlistNames.add((String) playlist.get("name"));
            }
        }
        
        logger.info("User has {} playlists", playlistNames.size());
        return playlistNames;
    }
    
    /**
     * Create a new playlist for the authenticated user
     */
    public Map<String, Object> createPlaylist(String accessToken, String userId, String name, String description) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", name);
            requestBody.put("description", description);
            requestBody.put("public", false);
            
            Map<String, Object> response = webClient.post()
                    .uri("https://api.spotify.com/v1/users/" + userId + "/playlists")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            logger.info("Created playlist: {}", name);
            return response;
        } catch (WebClientResponseException e) {
            logger.error("Error creating playlist: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Add tracks to a playlist
     */
    public boolean addTracksToPlaylist(String accessToken, String playlistId, List<String> trackUris) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("uris", trackUris);
            
            webClient.post()
                    .uri("https://api.spotify.com/v1/playlists/" + playlistId + "/tracks")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            logger.info("Added {} tracks to playlist {}", trackUris.size(), playlistId);
            return true;
        } catch (WebClientResponseException e) {
            logger.error("Error adding tracks to playlist: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Create a new playlist with tracks
     */
    public Map<String, Object> createPlaylistWithTracks(String accessToken, String userId, FridayPlaylist playlist) {
        // Create the playlist
        Map<String, Object> createdPlaylist = createPlaylist(
                accessToken,
                userId,
                playlist.getName(),
                "Friday Spotify links from WhatsApp group chat"
        );
        
        if (createdPlaylist.isEmpty() || !createdPlaylist.containsKey("id")) {
            logger.error("Failed to create playlist {}", playlist.getName());
            return Collections.emptyMap();
        }
        
        // Add tracks to the playlist
        String playlistId = (String) createdPlaylist.get("id");
        boolean tracksAdded = addTracksToPlaylist(accessToken, playlistId, playlist.getTrackUris());
        
        if (!tracksAdded) {
            logger.error("Failed to add tracks to playlist {}", playlist.getName());
        }
        
        return createdPlaylist;
    }
    
    /**
     * Get the authenticated user's profile
     */
    public Map<String, Object> getUserProfile(String accessToken) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("https://api.spotify.com/v1/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            logger.info("Retrieved user profile");
            return response != null ? response : Collections.emptyMap();
        } catch (WebClientResponseException e) {
            logger.error("Error retrieving user profile: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
