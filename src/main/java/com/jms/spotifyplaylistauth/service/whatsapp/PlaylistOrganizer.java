package com.jms.spotifyplaylistauth.service.whatsapp;

import com.jms.spotifyplaylistauth.dto.FridayPlaylist;
import com.jms.spotifyplaylistauth.dto.WhatsAppMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PlaylistOrganizer {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistOrganizer.class);
    
    // Used for creating playlist names in the format "JMS DD.MM.YY"
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");
    
    // Pattern to extract track ID from Spotify URL/URI
    private static final Pattern TRACK_ID_PATTERN = Pattern.compile("(?:track:|track/)(\\w+)");

    /**
     * Organizes messages into playlists by Friday date
     */
    public List<FridayPlaylist> organizeFridayPlaylists(List<WhatsAppMessage> messages, Set<String> existingPlaylistNames) {
        // Group messages by the Friday they were sent
        Map<LocalDate, List<WhatsAppMessage>> messagesByFriday = new HashMap<>();
        
        for (WhatsAppMessage message : messages) {
            if (message.isFriday() && message.hasSpotifyLink()) {
                LocalDate fridayDate = message.getTimestamp().toLocalDate();
                messagesByFriday.computeIfAbsent(fridayDate, k -> new ArrayList<>()).add(message);
            }
        }
        
        logger.info("Grouped messages by {} Fridays", messagesByFriday.size());
        
        // Create playlists for each Friday if they don't already exist
        List<FridayPlaylist> playlists = new ArrayList<>();
        
        for (Map.Entry<LocalDate, List<WhatsAppMessage>> entry : messagesByFriday.entrySet()) {
            LocalDate fridayDate = entry.getKey();
            List<WhatsAppMessage> fridayMessages = entry.getValue();
            
            // Create a playlist for this Friday
            LocalDateTime fridayDateTime = LocalDateTime.of(fridayDate, LocalTime.MIDNIGHT);
            FridayPlaylist playlist = new FridayPlaylist(fridayDateTime);
            
            // Skip if this playlist already exists
            if (existingPlaylistNames.contains(playlist.getName())) {
                logger.info("Playlist {} already exists, skipping", playlist.getName());
                continue;
            }
            
            // Extract Spotify track URIs from the messages
            for (WhatsAppMessage message : fridayMessages) {
                String trackUri = extractTrackUriFromLink(message.getSpotifyLink());
                if (trackUri != null) {
                    playlist.addTrackUri(trackUri);
                    logger.debug("Added track URI to playlist {}: {}", playlist.getName(), trackUri);
                }
            }
            
            // Only add playlists with tracks
            if (playlist.getTrackCount() > 0) {
                playlists.add(playlist);
                logger.info("Created playlist {} with {} tracks", playlist.getName(), playlist.getTrackCount());
            } else {
                logger.info("No valid tracks found for playlist {}, skipping", playlist.getName());
            }
        }
        
        // Sort playlists by date (oldest first)
        playlists.sort(Comparator.comparing(FridayPlaylist::getDate));
        
        return playlists;
    }
    
    /**
     * Extracts a Spotify track URI from a link
     * Examples:
     * https://open.spotify.com/track/1234567890 -> spotify:track:1234567890
     * https://open.spotify.com/track/1234567890?si=abcdef -> spotify:track:1234567890
     * spotify:track:1234567890 -> spotify:track:1234567890
     */
    private String extractTrackUriFromLink(String spotifyLink) {
        if (spotifyLink == null) return null;
        
        try {
            // If it's already a Spotify URI for a track, return it directly
            if (spotifyLink.startsWith("spotify:track:")) {
                logger.debug("Link is already a track URI: {}", spotifyLink);
                return spotifyLink;
            }
            
            // Extract track ID using regex
            Matcher matcher = TRACK_ID_PATTERN.matcher(spotifyLink);
            if (matcher.find()) {
                String trackId = matcher.group(1);
                if (trackId != null && !trackId.isEmpty()) {
                    String trackUri = "spotify:track:" + trackId;
                    logger.debug("Extracted track URI from link: {} -> {}", spotifyLink, trackUri);
                    return trackUri;
                }
            }
            
            logger.warn("Could not extract track URI from link: {}", spotifyLink);
        } catch (Exception e) {
            logger.warn("Failed to extract track URI from link: {}", spotifyLink, e);
        }
        
        return null;
    }
    
    /**
     * Gets the names of playlists that would be created from these messages
     */
    public Set<String> getPlaylistNamesFromMessages(List<WhatsAppMessage> messages) {
        return messages.stream()
                .filter(m -> m.isFriday() && m.hasSpotifyLink())
                .map(m -> {
                    LocalDate fridayDate = m.getTimestamp().toLocalDate();
                    return "JMS " + fridayDate.format(DATE_FORMATTER);
                })
                .collect(Collectors.toSet());
    }
}
