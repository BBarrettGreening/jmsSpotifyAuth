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
     * Get existing playlist names from the JMS website
     */
    public Set<String> getJmsWebsitePlaylists() {
        return jmsWebsiteService.fetchExistingPlaylistNames();
    }
    
    /**
     * Process a WhatsApp chat export file and create playlists for Fridays
     */
    public List<FridayPlaylist> processWhatsAppChatExport(MultipartFile file, String accessToken) throws IOException {
        // Parse the WhatsApp chat export
        List<WhatsAppMessage> messages = whatsAppParser.parseWhatsAppChatExport(file);
        
        // Filter for messages with Spotify links sent on Fridays
        List<WhatsAppMessage> fridaySpotifyMessages = whatsAppParser.filterFridaySpotifyMessages(messages);
        
        // Get existing playlist names from JMS website to avoid duplicates
        Set<String> existingPlaylistNames = jmsWebsiteService.fetchExistingPlaylistNames();
        logger.info("Fetched {} existing playlist names from JMS website", existingPlaylistNames.size());
        
        // Also check user's playlists as a fallback
        Set<String> userPlaylistNames = spotifyPlaylistService.getUserPlaylistNames(accessToken);
        existingPlaylistNames.addAll(userPlaylistNames);
        logger.info("Combined with {} user playlist names, total {} existing playlists to check", 
                userPlaylistNames.size(), existingPlaylistNames.size());
        
        // Organize messages into playlists by Friday
        List<FridayPlaylist> playlists = playlistOrganizer.organizeFridayPlaylists(fridaySpotifyMessages, existingPlaylistNames);
        
        logger.info("Created {} new Friday playlists", playlists.size());
        return playlists;
    }
    
    /**
     * Create playlists in Spotify for each Friday
     */
    public List<Map<String, Object>> createSpotifyPlaylists(List<FridayPlaylist> playlists, String accessToken) {
        List<Map<String, Object>> createdPlaylists = new ArrayList<>();
        
        // Get the user's Spotify ID
        Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
        if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
            logger.error("Failed to retrieve user profile");
            return createdPlaylists;
        }
        
        String userId = (String) userProfile.get("id");
        
        // Create each playlist
        for (FridayPlaylist playlist : playlists) {
            Map<String, Object> createdPlaylist = spotifyPlaylistService.createPlaylistWithTracks(
                    accessToken,
                    userId,
                    playlist
            );
            
            if (!createdPlaylist.isEmpty()) {
                createdPlaylists.add(createdPlaylist);
                logger.info("Created playlist {} with {} tracks", playlist.getName(), playlist.getTrackCount());
            }
        }
        
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