package com.jms.spotifyplaylistauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for exporting Spotify playlists to JMS JSON format
 */
@Service
public class PlaylistExportService {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistExportService.class);

    private final SpotifyPlaylistService spotifyPlaylistService;
    private final JmsWebsiteService jmsWebsiteService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PlaylistExportService(
            SpotifyPlaylistService spotifyPlaylistService,
            JmsWebsiteService jmsWebsiteService,
            ObjectMapper objectMapper) {
        this.spotifyPlaylistService = spotifyPlaylistService;
        this.jmsWebsiteService = jmsWebsiteService;
        this.objectMapper = objectMapper;
    }

    /**
     * Export user's Spotify playlists in JMS JSON format
     * @param accessToken Spotify access token
     * @return JSON string of playlists data
     */
    public String exportPlaylists(String accessToken) {
        logger.info("Exporting playlists with access token");

        try {
            // Get user profile
            Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
            if (userProfile.isEmpty() || !userProfile.containsKey("id")) {
                logger.error("Unable to retrieve user profile for export");
                throw new IllegalArgumentException("Unable to retrieve user profile - token may be invalid");
            }

            String userId = (String) userProfile.get("id");
            logger.info("Exporting playlists for user ID: {}", userId);

            // Get all playlists from the user's Spotify account
            List<Map<String, Object>> playlists = spotifyPlaylistService.getUserPlaylists(accessToken);
            logger.info("Retrieved {} playlists from Spotify", playlists.size());

            // Log all playlists to debug what's available
            logger.debug("Listing all user's playlists from Spotify:");
            for (Map<String, Object> playlist : playlists) {
                String name = (String) playlist.get("name");
                String id = (String) playlist.get("id");
                if (name != null && (name.contains("Weekly Mix") || name.contains("JMS Mix"))) {
                    logger.info("  - {} ({})", name, id);
                }
            }

            // Get existing playlists from JMS website
            Set<String> existingPlaylists = jmsWebsiteService.fetchExistingPlaylistNames();
            logger.info("Found {} existing playlists on JMS website", existingPlaylists.size());

            // Get existing playlist data from JMS website API if available
            List<Map<String, Object>> existingPlaylistsList = new ArrayList<>();
            try {
                existingPlaylistsList = getExistingPlaylistsData();
                logger.info("Retrieved {} playlists from JMS API", existingPlaylistsList.size());
            } catch (Exception e) {
                logger.warn("Could not retrieve existing playlist data, will generate new format: {}", e.getMessage());
            }

            // Create a map to hold our combined playlists (existing + new)
            Map<String, Map<String, Object>> combinedPlaylists = new HashMap<>();

            // Add existing JMS playlists first
            for (Map<String, Object> playlist : existingPlaylistsList) {
                String title = (String) playlist.get("title");
                if (title != null) {
                    combinedPlaylists.put(title, playlist);
                    logger.debug("Added existing playlist from JMS: {}", title);
                }
            }

            // Filter for valid weekly mix or JMS playlists and add new ones
            Pattern weeklyMixPattern = Pattern.compile("Weekly Mix (\\d{1,2}\\.\\d{2}\\.\\d{4})");
            Pattern jmsMixPattern = Pattern.compile("JMS Mix (\\d+)");
            // Pattern for the new format "JMS DD.MM.YY"
            Pattern jmsDatePattern = Pattern.compile("JMS (\\d{1,2}\\.\\d{2}\\.\\d{2})");

            for (Map<String, Object> playlist : playlists) {
                String name = (String) playlist.get("name");
                String id = (String) playlist.get("id");

                // Skip if name is null or empty
                if (name == null || name.isEmpty() || id == null) {
                    continue;
                }

                // Log all Weekly Mix and JMS Mix playlists for debugging
                if (name.contains("Weekly Mix") || name.contains("JMS Mix")) {
                    logger.info("Found potential playlist for export: {} ({})", name, id);
                }

                // Check if it's a Weekly Mix, JMS Mix, or JMS date format
                Matcher weeklyMatcher = weeklyMixPattern.matcher(name);
                Matcher jmsMatcher = jmsMixPattern.matcher(name);
                Matcher jmsDateMatcher = jmsDatePattern.matcher(name);

                // Handle JMS date format (JMS DD.MM.YY) specifically
                if (jmsDateMatcher.find()) {
                    // Transform "JMS DD.MM.YY" to "Weekly Mix DD.MM.YYYY"
                    String datePart = jmsDateMatcher.group(1); // Gets DD.MM.YY
                    String[] dateParts = datePart.split("\\.");
                    if (dateParts.length == 3) {
                        String day = dateParts[0];
                        String month = dateParts[1];
                        String year = dateParts[2];
                        // Convert 2-digit year to 4-digit (assuming 20XX)
                        String fourDigitYear = "20" + year;
                        name = "Weekly Mix " + day + "." + month + "." + fourDigitYear;
                        logger.info("Transformed playlist name from {} to {}", playlist.get("name"), name);
                    }
                }

                // Recheck with the potentially transformed name
                weeklyMatcher = weeklyMixPattern.matcher(name);

                if (weeklyMatcher.find() || jmsMatcher.find()) {
                    // Always update with the latest Spotify data - don't skip if it already exists
                    // Create the embed link
                    String embedLink = "https://open.spotify.com/embed/playlist/" + id + "?utm_source=generator";

                    // Determine tag based on playlist name
                    List<String> tags = new ArrayList<>();
                    tags.add("All");

                    if (name.startsWith("Weekly Mix")) {
                        tags.add("Weekly");
                    } else if (name.startsWith("JMS Mix")) {
                        tags.add("JMS");
                    } else if (name.startsWith("JMS ")) {
                        // For the original "JMS DD.MM.YY" format, we still want to add the Weekly tag
                        // since it's transformed to "Weekly Mix"
                        tags.add("Weekly");
                    }

                    // Create the playlist object
                    Map<String, Object> playlistObject = new HashMap<>();
                    playlistObject.put("title", name);
                    playlistObject.put("link", embedLink);
                    playlistObject.put("tag", tags);

                    // Add to combined playlists
                    combinedPlaylists.put(name, playlistObject);
                    logger.info("Added new playlist to export: {}", name);
                }
            }

            // Add recently created playlists that may not be in the combined list yet
            List<Map<String, Object>> recentPlaylists = spotifyPlaylistService.getRecentlyCreatedPlaylists(userId);
            logger.info("Found {} recently created playlists for user", recentPlaylists.size());

            for (Map<String, Object> recentPlaylist : recentPlaylists) {
                String name = (String) recentPlaylist.get("name");
                String id = (String) recentPlaylist.get("id");

                if (name != null && id != null) {
                    // Check for JMS date format (JMS DD.MM.YY) first
                    Matcher jmsDateMatcher = jmsDatePattern.matcher(name);
                    if (jmsDateMatcher.find()) {
                        // Transform "JMS DD.MM.YY" to "Weekly Mix DD.MM.YYYY"
                        String datePart = jmsDateMatcher.group(1); // Gets DD.MM.YY
                        String[] dateParts = datePart.split("\\.");
                        if (dateParts.length == 3) {
                            String day = dateParts[0];
                            String month = dateParts[1];
                            String year = dateParts[2];
                            // Convert 2-digit year to 4-digit (assuming 20XX)
                            String fourDigitYear = "20" + year;
                            name = "Weekly Mix " + day + "." + month + "." + fourDigitYear;
                            logger.info("Transformed recent playlist name from {} to {}", recentPlaylist.get("name"), name);
                        }
                    }

                    // Recheck with the potentially transformed name
                    Matcher weeklyMatcher = weeklyMixPattern.matcher(name);
                    Matcher jmsMatcher = jmsMixPattern.matcher(name);

                    if (weeklyMatcher.find() || jmsMatcher.find()) {
                        logger.info("Processing recently created playlist: {} ({})", name, id);
                        String embedLink = "https://open.spotify.com/embed/playlist/" + id + "?utm_source=generator";

                        List<String> tags = new ArrayList<>();
                        tags.add("All");

                        if (name.startsWith("Weekly Mix")) {
                            tags.add("Weekly");
                        } else if (name.startsWith("JMS Mix")) {
                            tags.add("JMS");
                        } else if (name.startsWith("JMS ")) {
                            // For the original "JMS DD.MM.YY" format, we still want to add the Weekly tag
                            // since it's transformed to "Weekly Mix"
                            tags.add("Weekly");
                        }

                        Map<String, Object> playlistObject = new HashMap<>();
                        playlistObject.put("title", name);
                        playlistObject.put("link", embedLink);
                        playlistObject.put("tag", tags);

                        combinedPlaylists.put(name, playlistObject);
                        logger.info("Added recently created playlist to export: {}", name);
                    }
                }
            }

            // Log combined playlists
            logger.info("Combined playlists count: {}", combinedPlaylists.size());

            // Now sort by date and assign IDs
            List<Map<String, Object>> sortedPlaylists = sortPlaylistsByDate(new ArrayList<>(combinedPlaylists.values()));

            // Create the final JSON array with proper IDs to match the provided format
            ArrayNode jsonArray = objectMapper.createArrayNode();

            // Assign IDs in descending order starting from the count down to 1
            int idCounter = sortedPlaylists.size();
            for (Map<String, Object> playlist : sortedPlaylists) {
                ObjectNode jsonObject = objectMapper.createObjectNode();
                jsonObject.put("id", idCounter--);
                jsonObject.put("title", (String) playlist.get("title"));
                jsonObject.put("link", (String) playlist.get("link"));

                ArrayNode tagsArray = jsonObject.putArray("tag");
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) playlist.get("tag");
                for (String tag : tags) {
                    tagsArray.add(tag);
                }

                jsonArray.add(jsonObject);
            }

            // Convert to pretty-printed JSON string that matches the provided format
            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonArray);
            logger.info("Successfully exported {} playlists to JSON", jsonArray.size());
            return jsonOutput;

        } catch (Exception e) {
            logger.error("Error exporting playlists: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export playlists: " + e.getMessage(), e);
        }
    }

    /**
     * Sort playlists by date, most recent first, with JMS Mix at end
     */
    private List<Map<String, Object>> sortPlaylistsByDate(List<Map<String, Object>> playlists) {
        // First, separate JMS Mix playlists from Weekly Mix playlists
        List<Map<String, Object>> jmsMixPlaylists = new ArrayList<>();
        List<Map<String, Object>> weeklyMixPlaylists = new ArrayList<>();

        for (Map<String, Object> playlist : playlists) {
            String title = (String) playlist.get("title");

            if (title == null) {
                continue;
            }

            if (title.startsWith("JMS Mix ")) {
                jmsMixPlaylists.add(playlist);
            } else if (title.startsWith("Weekly Mix ")) {
                weeklyMixPlaylists.add(playlist);
            } else {
                weeklyMixPlaylists.add(playlist); // Add any other playlists to weekly mix list
            }
        }

        // Create patterns to extract date information
        Pattern weeklyMixPattern = Pattern.compile("Weekly Mix (\\d{1,2}\\.\\d{2}\\.\\d{4})");
        Pattern jmsMixPattern = Pattern.compile("JMS Mix (\\d+)");
        Pattern jmsDatePattern = Pattern.compile("JMS (\\d{1,2}\\.\\d{2}\\.\\d{2})");

        // Sort Weekly Mix playlists by date (most recent first)
        Collections.sort(weeklyMixPlaylists, (p1, p2) -> {
            String title1 = (String) p1.get("title");
            String title2 = (String) p2.get("title");

            if (title1 == null || title2 == null) {
                return 0;
            }

            // Handle Weekly Mix with dates
            Matcher matcher1 = weeklyMixPattern.matcher(title1);
            Matcher matcher2 = weeklyMixPattern.matcher(title2);

            if (matcher1.find() && matcher2.find()) {
                String date1 = matcher1.group(1);
                String date2 = matcher2.group(1);

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
                    Date d1 = sdf.parse(date1);
                    Date d2 = sdf.parse(date2);

                    // Sort in descending order (most recent first)
                    return d2.compareTo(d1);
                } catch (Exception e) {
                    logger.warn("Error parsing playlist dates: {}", e.getMessage());
                }
            }

            // Default to string comparison
            return title1.compareTo(title2);
        });

        // Sort JMS Mix playlists by number (highest first)
        Collections.sort(jmsMixPlaylists, (p1, p2) -> {
            String title1 = (String) p1.get("title");
            String title2 = (String) p2.get("title");

            if (title1 == null || title2 == null) {
                return 0;
            }

            // Handle JMS Mix with numbers
            Matcher jmsMatcher1 = jmsMixPattern.matcher(title1);
            Matcher jmsMatcher2 = jmsMixPattern.matcher(title2);

            if (jmsMatcher1.find() && jmsMatcher2.find()) {
                try {
                    int num1 = Integer.parseInt(jmsMatcher1.group(1));
                    int num2 = Integer.parseInt(jmsMatcher2.group(1));

                    // Sort in descending order (highest number first)
                    return Integer.compare(num2, num1);
                } catch (Exception e) {
                    logger.warn("Error parsing JMS Mix numbers: {}", e.getMessage());
                }
            }

            // Default to string comparison
            return title1.compareTo(title2);
        });

        // Combine the lists with Weekly Mix playlists first, then JMS Mix playlists
        List<Map<String, Object>> result = new ArrayList<>(weeklyMixPlaylists);
        result.addAll(jmsMixPlaylists);

        return result;
    }

    /**
     * Get existing playlists data from JMS website
     */
    private List<Map<String, Object>> getExistingPlaylistsData() {
        try {
            String jsonData = jmsWebsiteService.fetchPlaylistsJson();

            if (jsonData != null && !jsonData.isEmpty()) {
                List<Map<String, Object>> playlists = objectMapper.readValue(
                        jsonData, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

                logger.info("Successfully parsed JSON data for {} existing playlists", playlists.size());
                return playlists;
            }
        } catch (Exception e) {
            logger.error("Error getting existing playlists data: {}", e.getMessage(), e);
        }

        return new ArrayList<>();
    }
}