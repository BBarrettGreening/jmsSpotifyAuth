package com.jms.spotifyplaylistauth.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhatsAppMessage {
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppMessage.class);
    
    // Patterns to match different Spotify link formats
    private static final Pattern SPOTIFY_URL_PATTERN = Pattern.compile("(https?://(?:open\\.)?spotify\\.com/(?:track|album|playlist|artist)/[a-zA-Z0-9]+(?:\\?[^\\s]*)?)");
    private static final Pattern SPOTIFY_URI_PATTERN = Pattern.compile("(spotify:(?:track|album|playlist|artist):[a-zA-Z0-9]+)");
    
    private LocalDateTime timestamp;
    private String sender;
    private String content;
    private String spotifyLink;
    private boolean isFriday;

    public WhatsAppMessage(LocalDateTime timestamp, String sender, String content) {
        this.timestamp = timestamp;
        this.sender = sender;
        this.content = content;
        this.spotifyLink = extractSpotifyLink(content);
        this.isFriday = timestamp.getDayOfWeek().getValue() == 5; // 5 is Friday
    }

    private String extractSpotifyLink(String content) {
        if (content == null) return null;
        
        // Try to match URL format first (more common in WhatsApp)
        Matcher urlMatcher = SPOTIFY_URL_PATTERN.matcher(content);
        if (urlMatcher.find()) {
            String url = urlMatcher.group(1);
            
            // Clean the URL by removing query parameters if needed
            if (url.contains("?")) {
                url = url.substring(0, url.indexOf("?"));
            }
            
            logger.debug("Found Spotify URL: {}", url);
            return url;
        }
        
        // Try to match URI format
        Matcher uriMatcher = SPOTIFY_URI_PATTERN.matcher(content);
        if (uriMatcher.find()) {
            String uri = uriMatcher.group(1);
            logger.debug("Found Spotify URI: {}", uri);
            return uri;
        }
        
        // Try a more lenient approach to match Spotify URLs that might have been improperly formatted
        if (content.contains("spotify.com") && content.contains("/track/")) {
            try {
                // Extract everything between "spotify.com/track/" and the next whitespace or end of string
                int startIndex = content.indexOf("spotify.com/track/") + "spotify.com/track/".length();
                int endIndex = content.indexOf(" ", startIndex);
                if (endIndex == -1) endIndex = content.length();
                
                // Extract the track ID
                String trackId = content.substring(startIndex, endIndex);
                
                // Remove any query parameters or other characters
                if (trackId.contains("?")) {
                    trackId = trackId.substring(0, trackId.indexOf("?"));
                }
                
                // Clean up any other unexpected characters
                trackId = trackId.replaceAll("[^a-zA-Z0-9]", "");
                
                if (!trackId.isEmpty()) {
                    String url = "https://open.spotify.com/track/" + trackId;
                    logger.debug("Extracted Spotify URL from imperfect format: {}", url);
                    return url;
                }
            } catch (Exception e) {
                logger.warn("Error attempting to extract Spotify link from imperfect format", e);
            }
        }
        
        return null;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getAuthor() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public String getSpotifyLink() {
        return spotifyLink;
    }

    public boolean isFriday() {
        return isFriday;
    }

    public boolean hasSpotifyLink() {
        return spotifyLink != null;
    }

    @Override
    public String toString() {
        return "WhatsAppMessage{" +
                "timestamp=" + timestamp +
                ", sender='" + sender + '\'' +
                ", content='" + (content != null && content.length() > 30 ? content.substring(0, 30) + "..." : content) + '\'' +
                ", spotifyLink='" + spotifyLink + '\'' +
                ", isFriday=" + isFriday +
                '}';
    }
}
