package com.jms.spotifyplaylistauth.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jms.spotifyplaylistauth.service.SpotifyPlaylistService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for playlist undo functionality
 */
@RestController
@RequestMapping("/api/playlists")
public class PlaylistUndoController {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistUndoController.class);
    
    private final SpotifyPlaylistService spotifyPlaylistService;
    
    @Autowired
    public PlaylistUndoController(SpotifyPlaylistService spotifyPlaylistService) {
        this.spotifyPlaylistService = spotifyPlaylistService;
    }
    
    /**
     * Get recently created playlists for a user
     * @param accessToken Spotify access token
     * @return List of recently created playlists
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentPlaylists(@RequestParam String accessToken) {
        try {
            // Get user profile to get user ID
            Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
            
            if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unable to retrieve user profile - token may be invalid"));
            }
            
            String userId = (String) userProfile.get("id");
            List<Map<String, Object>> recentPlaylists = spotifyPlaylistService.getRecentlyCreatedPlaylists(userId);
            
            return ResponseEntity.ok(Map.of(
                "userId", userId,
                "recentPlaylists", recentPlaylists
            ));
        } catch (Exception e) {
            logger.error("Error getting recent playlists: {}", e.getMessage(), e);
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get recent playlists: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
    
    /**
     * Undo recently created playlists
     * @param accessToken Spotify access token
     * @return Result of the undo operation
     */
    @PostMapping("/undo")
    public ResponseEntity<?> undoRecentPlaylists(@RequestParam String accessToken) {
        try {
            // Get user profile to get user ID
            Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
            
            if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unable to retrieve user profile - token may be invalid"));
            }
            
            String userId = (String) userProfile.get("id");
            List<String> undonePlaylistNames = spotifyPlaylistService.undoRecentPlaylistCreation(accessToken, userId);
            
            if (undonePlaylistNames.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "No recently created playlists found to undo",
                    "undoneCount", 0
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully removed " + undonePlaylistNames.size() + " recently created playlists",
                    "undoneCount", undonePlaylistNames.size(),
                    "undonePlaylistNames", undonePlaylistNames
                ));
            }
        } catch (Exception e) {
            logger.error("Error undoing recent playlists: {}", e.getMessage(), e);
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to undo recent playlists: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
}