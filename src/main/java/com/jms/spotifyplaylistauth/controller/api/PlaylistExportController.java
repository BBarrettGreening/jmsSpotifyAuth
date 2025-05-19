package com.jms.spotifyplaylistauth.controller.api;

import com.jms.spotifyplaylistauth.service.PlaylistExportService;
import com.jms.spotifyplaylistauth.service.SpotifyPlaylistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for playlist export functionality
 */
@RestController
@RequestMapping("/api/playlists")
public class PlaylistExportController {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistExportController.class);
    
    private final PlaylistExportService playlistExportService;
    private final SpotifyPlaylistService spotifyPlaylistService;
    
    @Autowired
    public PlaylistExportController(
            PlaylistExportService playlistExportService,
            SpotifyPlaylistService spotifyPlaylistService) {
        this.playlistExportService = playlistExportService;
        this.spotifyPlaylistService = spotifyPlaylistService;
    }
    
    /**
     * Export playlists in JMS JSON format
     * @param accessToken Spotify access token
     * @return JSON response with playlists data
     */
    @GetMapping("/export")
    public ResponseEntity<?> exportPlaylists(@RequestParam String accessToken) {
        logger.info("Playlist export requested with access token");
        
        try {
            // First, check if the user is valid
            Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
            if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unable to retrieve user profile - token may be invalid"));
            }
            
            // Call the service to generate the export
            String jsonData = playlistExportService.exportPlaylists(accessToken);
            
            // Return the JSON data
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonData);
        } catch (Exception e) {
            logger.error("Error exporting playlists: {}", e.getMessage(), e);
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to export playlists: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
    
    /**
     * Export playlists in JMS JSON format with debugging info
     * @param accessToken Spotify access token
     * @return JSON response with playlists data and debug info
     */
    @GetMapping("/export-debug")
    public ResponseEntity<?> exportPlaylistsWithDebug(@RequestParam String accessToken) {
        logger.info("Playlist export with debug requested with access token");
        
        try {
            // Get user profile
            Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
            if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unable to retrieve user profile - token may be invalid"));
            }
            
            String userId = (String) userProfile.get("id");
            
            // Get user's playlists for debugging
            List<Map<String, Object>> playlists = spotifyPlaylistService.getUserPlaylists(accessToken);
            
            // Filter for potential playlists matching our patterns
            List<Map<String, String>> filteredPlaylists = new ArrayList<>();
            for (Map<String, Object> playlist : playlists) {
                String name = (String) playlist.get("name");
                String id = (String) playlist.get("id");
                
                if (name != null && id != null && (name.contains("Weekly Mix") || name.contains("JMS Mix"))) {
                    filteredPlaylists.add(Map.of(
                        "name", name,
                        "id", id
                    ));
                }
            }
            
            // Call the service to generate the export
            String jsonData = playlistExportService.exportPlaylists(accessToken);
            
            // Combine debugging info with the JSON data
            Map<String, Object> response = new HashMap<>();
            response.put("user_id", userId);
            response.put("potential_playlists", filteredPlaylists);
            response.put("total_user_playlists", playlists.size());
            response.put("filtered_playlist_count", filteredPlaylists.size());
            response.put("export_data", jsonData);
            
            // Return the JSON data with debug info
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error exporting playlists with debug: {}", e.getMessage(), e);
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to export playlists: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
    
    /**
     * Download the playlist export as a JSON file
     * @param accessToken Spotify access token
     * @return JSON file download
     */
    @GetMapping("/export/download")
    public ResponseEntity<?> downloadPlaylistExport(@RequestParam String accessToken) {
        logger.info("Playlist export download requested with access token");
        
        try {
            // Call the service to generate the export
            String jsonData = playlistExportService.exportPlaylists(accessToken);
            
            // Generate a filename with the current timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "jms_playlists_" + timestamp + ".json";
            
            // Set up the response headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", filename);
            
            // Return the JSON data as a downloadable file
            return new ResponseEntity<>(jsonData, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error exporting playlists for download: {}", e.getMessage(), e);
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to export playlists: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
}