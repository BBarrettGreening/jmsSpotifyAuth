package com.jms.spotifyplaylistauth.service;

import com.jms.spotifyplaylistauth.dto.FridayPlaylist;
import com.jms.spotifyplaylistauth.dto.WhatsAppMessage;
import com.jms.spotifyplaylistauth.service.whatsapp.PlaylistOrganizer;
import com.jms.spotifyplaylistauth.service.whatsapp.WhatsAppParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class WhatsAppPlaylistService {
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppPlaylistService.class);
    
    private final WhatsAppParser whatsAppParser;
    private final PlaylistOrganizer playlistOrganizer;
    private final SpotifyPlaylistService spotifyPlaylistService;
    private final JmsWebsiteService jmsWebsiteService;
    
    @Autowired
    public WhatsAppPlaylistService(
            WhatsAppParser whatsAppParser,
            PlaylistOrganizer playlistOrganizer,
            SpotifyPlaylistService spotifyPlaylistService,
            JmsWebsiteService jmsWebsiteService) {
        this.whatsAppParser = whatsAppParser;
        this.playlistOrganizer = playlistOrganizer;
        this.spotifyPlaylistService = spotifyPlaylistService;
        this.jmsWebsiteService = jmsWebsiteService;
    }
    
    /**
     * Get JMS website playlists
     */
    public Set<String> getJmsWebsitePlaylists() {
        Set<String> playlists = jmsWebsiteService.fetchExistingPlaylistNames();
        
        // Add debug info
        logger.info("Got {} playlists from JMS website", playlists.size());
        
        // Log all playlists to debug
        logger.info("All JMS website playlists:");
        for (String playlist : playlists) {
            logger.info("  - {}", playlist);
        }
        
        // Log some key JMS playlists for debugging
        int count = 0;
        for (String playlist : playlists) {
            if (count < 5 && (playlist.startsWith("JMS ") || playlist.startsWith("Weekly Mix"))) {
                logger.info("Sample JMS website playlist: {}", playlist);
                count++;
            }
        }
        
        return playlists;
    }
    
    /**
     * Get user's Spotify playlists
     */
    public Set<String> getUserSpotifyPlaylists(String accessToken) {
        return spotifyPlaylistService.getUserPlaylistNames(accessToken);
    }
    
    /**
     * Process a WhatsApp chat export file and create playlists for Fridays
     */
    public List<FridayPlaylist> processWhatsAppChatExport(MultipartFile file, String accessToken) throws IOException {
        // Parse the WhatsApp chat export
        List<WhatsAppMessage> messages = whatsAppParser.parseWhatsAppChatExport(file);
        
        // Filter for messages with Spotify links sent on Fridays
        List<WhatsAppMessage> fridaySpotifyMessages = whatsAppParser.filterFridaySpotifyMessages(messages);
        
        // Get existing playlist names from JMS website API to avoid duplicates
        Set<String> jmsPlaylistNames = jmsWebsiteService.fetchExistingPlaylistNames();
        logger.info("Fetched {} existing playlist names from JMS website/API", jmsPlaylistNames.size());
        
        // Get the user's existing Spotify playlists 
        Set<String> userPlaylistNames = spotifyPlaylistService.getUserPlaylistNames(accessToken);
        logger.info("Fetched {} existing playlist names from user's Spotify account", userPlaylistNames.size());
        
        // Create a consolidated set of playlists that exist EITHER on JMS website OR in user's Spotify account
        // This ensures we only create playlists that don't exist in either place
        Set<String> allExistingPlaylistNames = new HashSet<>();
        
        // Process JMS playlists and also convert Weekly Mix format to JMS format
        for (String playlistName : jmsPlaylistNames) {
            allExistingPlaylistNames.add(playlistName);
            
            // If it's a Weekly Mix format, also add the JMS equivalent
            if (playlistName.startsWith("Weekly Mix ")) {
                String datePart = playlistName.substring("Weekly Mix ".length());
                try {
                    // Format: DD.MM.YYYY -> JMS DD.MM.YY
                    if (datePart.matches("\\d{1,2}\\.\\d{2}\\.\\d{4}")) {
                        String[] parts = datePart.split("\\.");
                        String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                        String month = parts[1];
                        String year = parts[2].substring(2); // Last 2 digits
                        String jmsTitle = "JMS " + day + "." + month + "." + year;
                        allExistingPlaylistNames.add(jmsTitle);
                        logger.debug("Added JMS equivalent to consolidated list: {}", jmsTitle);
                    }
                } catch (Exception ex) {
                    logger.warn("Error converting Weekly Mix date format for '{}': {}", playlistName, ex.getMessage());
                }
            }
        }
        
        // Add all Spotify playlists
        allExistingPlaylistNames.addAll(userPlaylistNames);
        
        logger.info("Combined total of {} existing playlists to check (JMS + Spotify)", allExistingPlaylistNames.size());
        
        // Log all playlists in the consolidated list for debugging
        logger.info("Consolidated playlist list (showing first 20 for brevity):");
        int displayCount = 0;
        for (String playlistName : allExistingPlaylistNames) {
            if (displayCount < 20) { // Limit logging to avoid overwhelming the logs
                logger.info("  - {}", playlistName);
                displayCount++;
            } else {
                break;
            }
        }
        
        // Log more comprehensive information about the existing playlists to help with debugging
        logger.info("Detailed analysis of detected playlists:");
        int jmsWebsiteCount = 0;
        int weeklyMixCount = 0;
        int jmsNamedCount = 0;
                
        for (String playlistName : jmsPlaylistNames) {
            if (playlistName.startsWith("JMS ")) {
                jmsNamedCount++;
                logger.info("Found existing JMS-format playlist on website: {}", playlistName);
            }
            else if (playlistName.startsWith("Weekly Mix ")) {
                weeklyMixCount++;
                logger.info("Found existing Weekly Mix playlist on website: {}", playlistName);
            }
            jmsWebsiteCount++;
        }
        
        logger.info("JMS Website Playlist Counts: Total={}, JMS Format={}, Weekly Mix={}", 
                     jmsWebsiteCount, jmsNamedCount, weeklyMixCount);
            
        // Clear counters for Spotify account
        int spotifyTotalCount = 0;
        int spotifyJmsCount = 0;
        int spotifyWeeklyCount = 0;
            
        for (String playlistName : userPlaylistNames) {
            if (playlistName.startsWith("JMS ")) {
                spotifyJmsCount++;
                logger.info("Found existing JMS-format playlist in Spotify: {}", playlistName);
            }
            else if (playlistName.startsWith("Weekly Mix ")) {
                spotifyWeeklyCount++;
                logger.info("Found existing Weekly Mix playlist in Spotify: {}", playlistName);
            }
            spotifyTotalCount++;
        }
        
        logger.info("Spotify Account Playlist Counts: Total={}, JMS Format={}, Weekly Mix={}", 
                    spotifyTotalCount, spotifyJmsCount, spotifyWeeklyCount);
        
        // Organize messages into playlists by Friday
        List<FridayPlaylist> playlists = playlistOrganizer.organizeFridayPlaylists(fridaySpotifyMessages, allExistingPlaylistNames);
        
        logger.info("Created {} new Friday playlists that don't exist on JMS or in Spotify account", playlists.size());
        return playlists;
    }
    
    /**
     * Create playlists in Spotify from the parsed data
     * @param playlists List of FridayPlaylist objects
     * @param accessToken Spotify access token
     * @return List of created playlist data
     */
    public List<Map<String, Object>> createSpotifyPlaylists(List<FridayPlaylist> playlists, String accessToken) {
        logger.info("Attempting to create {} playlists in Spotify", playlists.size());
        
        // Fetch user profile to get user ID
        Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
        
        if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
            logger.error("Unable to retrieve user profile for playlist creation");
            throw new IllegalArgumentException("Unable to retrieve user profile - token may be invalid");
        }
        
        String userId = (String) userProfile.get("id");
        logger.info("Creating playlists for user ID: {}", userId);
        
        // Clear any existing recently created playlists tracking for this user
        spotifyPlaylistService.clearRecentlyCreatedPlaylists(userId);
        
        List<Map<String, Object>> createdPlaylists = new ArrayList<>();
        
        for (FridayPlaylist playlist : playlists) {
            if (playlist.getTrackUris().isEmpty()) {
                logger.warn("Skipping playlist {} as it has no tracks", playlist.getName());
                continue;
            }
            
            try {
                Map<String, Object> createdPlaylist = spotifyPlaylistService.createPlaylistWithTracks(
                        accessToken, 
                        userId, 
                        playlist);
                
                if (!createdPlaylist.isEmpty()) {
                    createdPlaylists.add(createdPlaylist);
                    logger.info("Created playlist: {} with {} tracks", 
                             playlist.getName(), playlist.getTrackUris().size());
                } else {
                    logger.error("Failed to create playlist: {}", playlist.getName());
                }
            } catch (Exception e) {
                logger.error("Error creating playlist {}: {}", playlist.getName(), e.getMessage(), e);
            }
        }
        
        logger.info("Successfully created {} playlists in Spotify", createdPlaylists.size());
        return createdPlaylists;
    }
    
    /**
     * Process a WhatsApp chat export and create playlists in Spotify
     */
    public Map<String, Object> processAndCreatePlaylists(MultipartFile file, String accessToken) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        List<FridayPlaylist> playlists = processWhatsAppChatExport(file, accessToken);
        result.put("plannedPlaylists", playlists);
        
        if (!playlists.isEmpty()) {
            List<Map<String, Object>> createdPlaylists = createSpotifyPlaylists(playlists, accessToken);
            result.put("createdPlaylists", createdPlaylists);
            result.put("success", !createdPlaylists.isEmpty());
        } else {
            result.put("success", false);
            result.put("message", "No new Friday playlists to create");
        }
        
        return result;
    }
}