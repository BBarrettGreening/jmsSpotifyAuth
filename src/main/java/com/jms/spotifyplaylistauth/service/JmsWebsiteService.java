package com.jms.spotifyplaylistauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JmsWebsiteService {
    private static final Logger logger = LoggerFactory.getLogger(JmsWebsiteService.class);
    
    private final WebClient webClient;
    
    // Patterns to extract playlist names in the format "JMS DD.MM.YY"
    private static final Pattern PLAYLIST_NAME_PATTERN_1 = Pattern.compile("JMS\\s+(\\d{2}\\.\\d{2}\\.\\d{2})\\b");
    private static final Pattern PLAYLIST_NAME_PATTERN_2 = Pattern.compile("\"name\":\"JMS\\s+(\\d{2}\\.\\d{2}\\.\\d{2})\"");
    private static final Pattern PLAYLIST_NAME_PATTERN_3 = Pattern.compile("<title>JMS\\s+(\\d{2}\\.\\d{2}\\.\\d{2})");
    private static final Pattern PLAYLIST_NAME_PATTERN_4 = Pattern.compile("JMS[_\\s]+(\\d{2}[._]\\d{2}[._]\\d{2})");
    
    @Value("${jms.website.playlists-url:https://www.jurassicmusicsociety.com/playlists}")
    private String jmsPlaylistsUrl;
    
    @Value("${jms.api.playlists-url:https://www.jurassicmusicsociety.com/api/playlists}")
    private String jmsApiPlaylistsUrl;
    
    public JmsWebsiteService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * Fetch existing playlist names from the JMS website
     */
    public Set<String> fetchExistingPlaylistNames() {
        Set<String> combinedPlaylistNames = new HashSet<>();
        
        // Try to fetch from the main website first
        try {
            logger.info("Fetching existing playlist names from JMS website: {}", jmsPlaylistsUrl);
            
            String pageContent = webClient.get()
                    .uri(jmsPlaylistsUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (pageContent != null && !pageContent.isEmpty()) {
                // Extract playlist names from the page content
                Set<String> playlistNames = extractPlaylistNamesFromHtml(pageContent);
                logger.info("Found {} existing playlist names on JMS website", playlistNames.size());
                combinedPlaylistNames.addAll(playlistNames);
            } else {
                logger.warn("Received empty response from JMS website");
            }
        } catch (WebClientResponseException e) {
            logger.error("Error fetching JMS website: {} - {}", e.getStatusCode(), e.getStatusText());
        } catch (Exception e) {
            logger.error("Unexpected error fetching JMS website: {}", e.getMessage(), e);
        }
        
        // Try to fetch from the API as a fallback
        try {
            logger.info("Fetching existing playlist names from JMS API: {}", jmsApiPlaylistsUrl);
            
            // Try to get JSON response from the API
            Object[] playlistsJson = webClient.get()
                    .uri(jmsApiPlaylistsUrl)
                    .retrieve()
                    .bodyToMono(Object[].class)
                    .block();
            
            if (playlistsJson != null && playlistsJson.length > 0) {
                // Extract playlist names from JSON response
                Set<String> apiPlaylistNames = extractPlaylistNamesFromJson(playlistsJson);
                logger.info("Found {} existing playlist names from JMS API", apiPlaylistNames.size());
                combinedPlaylistNames.addAll(apiPlaylistNames);
            } else {
                logger.warn("Received empty or invalid response from JMS API");
            }
        } catch (WebClientResponseException e) {
            logger.error("Error fetching JMS API: {} - {}", e.getStatusCode(), e.getStatusText());
        } catch (Exception e) {
            logger.error("Unexpected error fetching JMS API: {}", e.getMessage(), e);
        }
        
        logger.info("Combined total of {} existing playlist names", combinedPlaylistNames.size());
        return combinedPlaylistNames;
    }
    
    /**
     * Extract playlist names from JSON response
     */
    private Set<String> extractPlaylistNamesFromJson(Object[] playlistsJson) {
        Set<String> playlistNames = new HashSet<>();
        
        for (Object playlistObj : playlistsJson) {
            try {
                if (playlistObj instanceof Map) {
                    Map<String, Object> playlist = (Map<String, Object>) playlistObj;
                    
                    if (playlist.containsKey("name")) {
                        String name = (String) playlist.get("name");
                        
                        // Check if it's a JMS playlist
                        if (name != null && name.startsWith("JMS ")) {
                            playlistNames.add(name);
                            logger.debug("Found existing playlist from API: {}", name);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error parsing playlist JSON: {}", e.getMessage());
            }
        }
        
        return playlistNames;
    }
    
    /**
     * Extract playlist names from the HTML content
     */
    private Set<String> extractPlaylistNamesFromHtml(String htmlContent) {
        Set<String> playlistNames = new HashSet<>();
        
        // Try each pattern to extract playlist names
        findPlaylistsWithPattern(playlistNames, htmlContent, PLAYLIST_NAME_PATTERN_1);
        findPlaylistsWithPattern(playlistNames, htmlContent, PLAYLIST_NAME_PATTERN_2);
        findPlaylistsWithPattern(playlistNames, htmlContent, PLAYLIST_NAME_PATTERN_3);
        findPlaylistsWithPattern(playlistNames, htmlContent, PLAYLIST_NAME_PATTERN_4);
        
        return playlistNames;
    }
    
    /**
     * Find playlists using a specific pattern
     */
    private void findPlaylistsWithPattern(Set<String> playlistNames, String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String date = matcher.group(1);
            
            // Normalize the date format if needed
            date = date.replaceAll("[_]", ".");
            
            String playlistName = "JMS " + date;
            playlistNames.add(playlistName);
            logger.debug("Found existing playlist: {}", playlistName);
        }
    }
}