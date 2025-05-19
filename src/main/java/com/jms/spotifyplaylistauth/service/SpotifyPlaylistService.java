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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpotifyPlaylistService {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyPlaylistService.class);
    
    private final WebClient webClient;
    
    // Store recently created playlists by user ID for undo functionality
    private final Map<String, List<Map<String, Object>>> recentlyCreatedPlaylists = new ConcurrentHashMap<>();
    
    @Autowired
    public SpotifyPlaylistService(WebClient webClient) {
        this.webClient = webClient;
    }
    
    /**
     * Track a newly created playlist for potential undo
     * @param userId Spotify user ID
     * @param playlistInfo Playlist information map
     */
    public void trackCreatedPlaylist(String userId, Map<String, Object> playlistInfo) {
        logger.info("Tracking newly created playlist for user {}: {}", userId, playlistInfo.get("name"));
        
        // Create a list for this user if it doesn't exist
        recentlyCreatedPlaylists.computeIfAbsent(userId, k -> new ArrayList<>());
        
        // Check if this playlist is already in the list to avoid duplicates
        String newPlaylistId = (String) playlistInfo.get("id");
        boolean alreadyTracked = false;
        
        if (newPlaylistId != null) {
            for (Map<String, Object> playlist : recentlyCreatedPlaylists.get(userId)) {
                String existingId = (String) playlist.get("id");
                if (newPlaylistId.equals(existingId)) {
                    logger.info("Playlist already tracked, skipping duplicate: {}", newPlaylistId);
                    alreadyTracked = true;
                    break;
                }
            }
        }
        
        // Add the playlist to the user's list if not already tracked
        if (!alreadyTracked) {
            recentlyCreatedPlaylists.get(userId).add(playlistInfo);
            logger.info("Added playlist to tracking: {}", playlistInfo.get("name"));
        }
    }
    
    /**
     * Get the list of recently created playlists for a user
     * @param userId Spotify user ID
     * @return List of recently created playlists
     */
    public List<Map<String, Object>> getRecentlyCreatedPlaylists(String userId) {
        return recentlyCreatedPlaylists.getOrDefault(userId, new ArrayList<>());
    }
    
    /**
     * Clear the list of recently created playlists for a user
     * @param userId Spotify user ID
     */
    public void clearRecentlyCreatedPlaylists(String userId) {
        logger.info("Clearing recently created playlists list for user {}", userId);
        recentlyCreatedPlaylists.remove(userId);
    }
    
    /**
     * Undo recently created playlists for a user
     * @param accessToken Spotify access token
     * @param userId Spotify user ID
     * @return List of undone playlist names
     */
    public List<String> undoRecentPlaylistCreation(String accessToken, String userId) {
        List<Map<String, Object>> playlists = getRecentlyCreatedPlaylists(userId);
        List<String> undonePlaylistNames = new ArrayList<>();
        
        if (playlists == null || playlists.isEmpty()) {
            logger.info("No recently created playlists found for user {}", userId);
            return undonePlaylistNames;
        }
        
        logger.info("Attempting to undo {} recently created playlists for user {}", playlists.size(), userId);
        
        // Create a list to keep track of playlists that weren't deleted successfully
        List<Map<String, Object>> remainingPlaylists = new ArrayList<>();
        
        for (Map<String, Object> playlist : playlists) {
            String playlistId = (String) playlist.get("id");
            String playlistName = (String) playlist.get("name");
            
            if (playlistId != null && !playlistId.isEmpty()) {
                if (deletePlaylist(accessToken, playlistId)) {
                    logger.info("Successfully undone playlist: {}", playlistName);
                    undonePlaylistNames.add(playlistName);
                } else {
                    logger.warn("Failed to undo playlist: {}", playlistName);
                    remainingPlaylists.add(playlist);
                }
            }
        }
        
        // Update the tracking list with only playlists that couldn't be deleted
        // instead of clearing the entire list
        if (remainingPlaylists.isEmpty()) {
            clearRecentlyCreatedPlaylists(userId);
            logger.info("Cleared all recently created playlists for user {}", userId);
        } else {
            // Replace with the list of playlists that couldn't be deleted
            recentlyCreatedPlaylists.put(userId, remainingPlaylists);
            logger.info("Updated recently created playlists for user {} - {} remain", userId, remainingPlaylists.size());
        }
        
        return undonePlaylistNames;
    }
    
    /**
     * Get all playlists for the authenticated user
     * This version fetches all pages of playlists
     */
    public List<Map<String, Object>> getUserPlaylists(String accessToken) {
        List<Map<String, Object>> allPlaylists = new ArrayList<>();
        String nextUrl = "https://api.spotify.com/v1/me/playlists?limit=50";
        
        try {
            while (nextUrl != null) {
                logger.info("Fetching playlists page: {}", nextUrl);
                
                Map<String, Object> response = webClient.get()
                        .uri(nextUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                
                if (response != null && response.containsKey("items")) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                    allPlaylists.addAll(items);
                    logger.info("Added {} playlists from current page", items.size());
                    
                    // Check if there are more pages
                    if (response.containsKey("next") && response.get("next") != null) {
                        nextUrl = (String) response.get("next");
                    } else {
                        nextUrl = null;
                    }
                } else {
                    logger.warn("No items found in playlist response");
                    nextUrl = null;
                }
            }
            
            logger.info("Retrieved a total of {} playlists for user", allPlaylists.size());
            return allPlaylists;
            
        } catch (WebClientResponseException e) {
            logger.error("Error retrieving user playlists: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get all playlists for the authenticated user and check for both exact name matches
     * and variations on the JMS naming pattern for better duplicate detection
     */
    public Set<String> getUserPlaylistNames(String accessToken) {
        List<Map<String, Object>> playlists = getUserPlaylists(accessToken);
        Set<String> playlistNames = new HashSet<>();
        
        // Map to store JMS formatted playlists (key: JMS DD.MM.YY format, value: original playlist name)
        Map<String, String> jmsFormatMap = new HashMap<>();
        
        // Pattern to match "Weekly Mix" date format (e.g., "Weekly Mix 21.02.2025")
        Pattern weeklyMixPattern = Pattern.compile("Weekly Mix (\\d{1,2}\\.\\d{2}\\.\\d{4})");
        
        // Pattern to match "JMS" format (e.g., "JMS 21.02.25")
        Pattern jmsPattern = Pattern.compile("JMS (\\d{1,2}\\.\\d{2}\\.\\d{2})");
        
        for (Map<String, Object> playlist : playlists) {
            if (playlist.containsKey("name")) {
                String name = (String) playlist.get("name");
                playlistNames.add(name);
                
                // Also check for "Weekly Mix" naming pattern and add JMS equivalent
                Matcher weeklyMatcher = weeklyMixPattern.matcher(name);
                if (weeklyMatcher.find()) { // Changed from matches() to find() to be more flexible
                    String datePart = weeklyMatcher.group(1);
                    try {
                        String[] parts = datePart.split("\\.");
                        String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                        String month = parts[1];
                        String year = parts[2].substring(2); // Last 2 digits
                        String jmsTitle = "JMS " + day + "." + month + "." + year;
                        playlistNames.add(jmsTitle);
                        jmsFormatMap.put(jmsTitle, name);
                        logger.debug("Added JMS equivalent for Weekly Mix: {} -> {}", name, jmsTitle);
                    } catch (Exception ex) {
                        logger.warn("Error converting Weekly Mix format: {}", ex.getMessage());
                    }
                }
                
                // Also standardize JMS pattern format for consistency
                Matcher jmsMatcher = jmsPattern.matcher(name);
                if (jmsMatcher.find()) { // Changed from matches() to find() to be more flexible
                    String datePart = jmsMatcher.group(1);
                    try {
                        String[] parts = datePart.split("\\.");
                        String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                        String month = parts[1];
                        String year = parts[2];
                        String jmsTitle = "JMS " + day + "." + month + "." + year;
                        playlistNames.add(jmsTitle);
                        jmsFormatMap.put(jmsTitle, name);
                        logger.debug("Standardized JMS format: {} -> {}", name, jmsTitle);
                    } catch (Exception ex) {
                        logger.warn("Error standardizing JMS format: {}", ex.getMessage());
                    }
                }
                
                // Additionally, check for pattern "JMS DD.MM.YY"
                Pattern fullJmsPattern = Pattern.compile("JMS (\\d{2}\\.\\d{2}\\.\\d{2})");
                Matcher fullJmsMatcher = fullJmsPattern.matcher(name);
                if (fullJmsMatcher.find()) {
                    // Extract the date part for better logging
                    String datePart = fullJmsMatcher.group(1);
                    logger.debug("Found direct JMS playlist with date {}: {}", datePart, name);
                }
            }
        }
        
        logger.info("User has {} playlists ({} with date format variations)", 
                  playlists.size(), playlistNames.size());
        
        // Log a sample of JMS format mappings
        int count = 0;
        for (Map.Entry<String, String> entry : jmsFormatMap.entrySet()) {
            if (count < 5) { // Only log a few examples
                logger.debug("JMS format mapping: {} -> {}", entry.getValue(), entry.getKey());
                count++;
            } else {
                break;
            }
        }
        
        return playlistNames;
    }
    
    /**
     * Create a new playlist for the authenticated user
     */
    public Map<String, Object> createPlaylist(String accessToken, String userId, String name, String description) {
        try {
            logger.info("Creating playlist: {}", name);
            
            // Check if a playlist with this name already exists
            List<Map<String, Object>> existingPlaylists = getUserPlaylists(accessToken);
            for (Map<String, Object> existingPlaylist : existingPlaylists) {
                String existingName = (String) existingPlaylist.get("name");
                if (existingName != null && existingName.equals(name)) {
                    logger.info("Playlist with name '{}' already exists, using existing playlist", name);
                    
                    // Track this playlist for potential undo as if we just created it
                    trackCreatedPlaylist(userId, existingPlaylist);
                    return existingPlaylist;
                }
            }
            
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
            
            logger.info("Created new playlist: {}", name);
            
            // Track the created playlist for potential undo
            if (response != null && response.containsKey("id")) {
                trackCreatedPlaylist(userId, response);
            }
            
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
        logger.info("Creating new playlist '{}' with {} tracks", playlist.getName(), playlist.getTrackUris().size());
        
        // Check if a playlist with this name already exists
        List<Map<String, Object>> existingPlaylists = getUserPlaylists(accessToken);
        String existingPlaylistId = null;
        
        for (Map<String, Object> existingPlaylist : existingPlaylists) {
            String name = (String) existingPlaylist.get("name");
            if (name != null && name.equals(playlist.getName())) {
                existingPlaylistId = (String) existingPlaylist.get("id");
                logger.info("Found existing playlist with same name: {} ({})", name, existingPlaylistId);
                break;
            }
        }
        
        Map<String, Object> createdPlaylist;
        
        // If a playlist with this name already exists, use it instead of creating a new one
        if (existingPlaylistId != null) {
            logger.info("Using existing playlist instead of creating a new one: {}", playlist.getName());
            
            // Get the existing playlist details
            createdPlaylist = new HashMap<>();
            createdPlaylist.put("id", existingPlaylistId);
            createdPlaylist.put("name", playlist.getName());
            
            // Update the tracks in the existing playlist
            // First, clear the existing tracks (not implemented here)
            // Then add the new tracks
            boolean tracksAdded = addTracksToPlaylist(accessToken, existingPlaylistId, playlist.getTrackUris());
            
            if (!tracksAdded) {
                logger.error("Failed to update tracks in existing playlist {}", playlist.getName());
            } else {
                logger.info("Successfully updated tracks in existing playlist {}", playlist.getName());
                
                // Track this playlist for potential undo (even though we didn't create it)
                trackCreatedPlaylist(userId, createdPlaylist);
            }
        } else {
            // Create a new playlist
            createdPlaylist = createPlaylist(
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
            } else {
                logger.info("Successfully created playlist {} with {} tracks", 
                          playlist.getName(), playlist.getTrackUris().size());
            }
        }
        
        return createdPlaylist;
    }
    
    /**
     * Get the authenticated user's profile
     */
    public Map<String, Object> getUserProfile(String accessToken) {
        try {
            logger.info("Attempting to retrieve user profile with access token length={}", 
                      accessToken != null ? accessToken.length() : 0);
            
            Map<String, Object> response = webClient.get()
                    .uri("https://api.spotify.com/v1/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("id")) {
                logger.info("Successfully retrieved user profile with ID: {}", response.get("id"));
                return response;
            } else {
                logger.error("Retrieved user profile response but it's missing the 'id' field: {}", response);
                return Collections.emptyMap();
            }
        } catch (WebClientResponseException e) {
            logger.error("HTTP Error retrieving user profile: {} {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response body: {}", e.getResponseBodyAsString());
            return Collections.emptyMap();
        } catch (Exception e) {
            logger.error("Error retrieving user profile: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Delete a playlist by its ID
     */
    public boolean deletePlaylist(String accessToken, String playlistId) {
        try {
            webClient.delete()
                    .uri("https://api.spotify.com/v1/playlists/" + playlistId + "/followers")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            
            logger.info("Successfully unfollowed/deleted playlist: {}", playlistId);
            return true;
        } catch (WebClientResponseException e) {
            logger.error("Error deleting playlist {}: {} {}", playlistId, e.getStatusCode(), e.getStatusText());
            logger.error("Response body: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.error("Error deleting playlist {}: {}", playlistId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Find and delete playlists by name pattern
     */
    public int deletePlaylists(String accessToken, String namePattern) {
        List<Map<String, Object>> playlists = getUserPlaylists(accessToken);
        int deletedCount = 0;
        
        for (Map<String, Object> playlist : playlists) {
            String name = (String) playlist.get("name");
            String id = (String) playlist.get("id");
            
            if (name != null && name.matches(namePattern) && id != null) {
                logger.info("Found playlist to delete: {} ({})", name, id);
                boolean deleted = deletePlaylist(accessToken, id);
                
                if (deleted) {
                    deletedCount++;
                    logger.info("Successfully deleted playlist: {} ({})", name, id);
                } else {
                    logger.warn("Failed to delete playlist: {} ({})", name, id);
                }
            }
        }
        
        return deletedCount;
    }
}