package com.jms.spotifyplaylistauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JmsWebsiteService {
    private static final Logger logger = LoggerFactory.getLogger(JmsWebsiteService.class);
    
    private final WebClient webClient;
    private final WebClient redirectAwareWebClient;
    
    // Patterns to extract playlist names in various formats
    private static final Pattern PLAYLIST_NAME_PATTERN_1 = Pattern.compile("JMS\\s+(\\d{2}\\.\\d{2}\\.\\d{2})\\b");
    private static final Pattern PLAYLIST_NAME_PATTERN_2 = Pattern.compile("\"name\":\"JMS\\s+(\\d{2}\\.\\d{2}\\.\\d{2})\"");
    private static final Pattern PLAYLIST_NAME_PATTERN_3 = Pattern.compile("<title>JMS\\s+(\\d{2}\\.\\d{2}\\.\\d{2})");
    private static final Pattern PLAYLIST_NAME_PATTERN_4 = Pattern.compile("JMS[_\\s]+(\\d{2}[._]\\d{2}[._]\\d{2})");
    private static final Pattern PLAYLIST_NAME_PATTERN_5 = Pattern.compile("JMS Mix (\\d+)");
    private static final Pattern PLAYLIST_NAME_PATTERN_6 = Pattern.compile("\"title\":\"JMS Mix (\\d+)\"");
    
    @Value("${jms.website.playlists-url:https://jurassicmusicsociety.com/playlists}")
private String jmsPlaylistsUrl;

@Value("${jms.api.playlists-url:https://jurassicmusicsociety.com/api/playlists}")
private String jmsApiPlaylistsUrl;

@Value("${jms.api.backup-url:https://raw.githubusercontent.com/deemanrip/api-data/main/jms-playlists.json}")
private String jmsBackupUrl;
    
    @Value("${jms.api.use-api-first:true}")
    private boolean useApiFirst;
    
    public JmsWebsiteService(WebClient.Builder webClientBuilder) {
        // Create an HttpClient that automatically follows redirects
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .wiretap(true); // Enable detailed wire logging for network requests
        
        logger.info("Initializing WebClient with automatic redirect following");
        // Configure base WebClient to log requests and increase buffer size
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // Increase buffer size
                .filter((request, next) -> {
                    // Custom filter to log request details for debugging
                    logger.debug("WebClient requesting: {} {}", request.method(), request.url());
                    return next.exchange(request);
                })
                .build();
        
        // The redirectAwareWebClient is the same as the regular webclient since we now have redirect following built in
        this.redirectAwareWebClient = this.webClient;
    }
    
    /**
     * Fetch existing playlist names from the JMS website and/or API
     */
    public Set<String> fetchExistingPlaylistNames() {
        Set<String> combinedPlaylistNames = new HashSet<>();
        
        // Start by fetching from the API
        boolean apiSuccess = fetchFromApi(combinedPlaylistNames);
        
        // If API fails, try to fetch from the website as fallback
        if (!apiSuccess) {
            logger.warn("API fetch failed, trying website as fallback");
            fetchFromWebsite(combinedPlaylistNames);
        }
        
        // If we still don't have any playlists, use the hardcoded data from paste.txt
        if (combinedPlaylistNames.isEmpty()) {
            logger.warn("No playlists found from API or website - using fallback data from resource");
            try {
                // Load the paste.txt file from resources directory
                org.springframework.core.io.Resource resource = 
                    new org.springframework.core.io.ClassPathResource("paste.txt");
                java.io.InputStream inputStream = resource.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(inputStream));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                String jsonData = content.toString();
                
                // Extract titles using regex
                Pattern titlePattern = Pattern.compile("\"title\":\"([^\"]+)\"");
                Matcher matcher = titlePattern.matcher(jsonData);
                int count = 0;
                while (matcher.find()) {
                    String title = matcher.group(1);
                    combinedPlaylistNames.add(title);
                    logger.debug("Extracted title from paste.txt resource: {}", title);
                    count++;
                    
                    // Also convert Weekly Mix to JMS format if applicable
                    if (title.startsWith("Weekly Mix ")) {
                        convertWeeklyMixToJmsFormat(title, combinedPlaylistNames);
                    }
                }
                
                logger.info("Extracted {} playlist titles from paste.txt resource", count);
            } catch (Exception e) {
                logger.error("Error reading or parsing paste.txt resource: {}", e.getMessage(), e);
                // Fallback to hardcoded snippet
                loadHardcodedPlaylistData(combinedPlaylistNames);
            }
        }
        
        logger.info("Combined total of {} existing playlist names", combinedPlaylistNames.size());
        return combinedPlaylistNames;
    }
    
    /**
     * Fetch JMS playlists from a specific API URL
     * Used primarily for testing
     */
    public Set<String> fetchJmsPlaylists(String apiUrl) {
        Set<String> playlistNames = new HashSet<>();
        
        try {
            logger.info("Fetching playlists from API URL: {}", apiUrl);
            
            // First try to get the response directly as a string using the redirect-aware client
            String rawResponse = redirectAwareWebClient.get()
                    .uri(apiUrl)
                    .exchangeToMono(response -> {
                        logger.info("Received response with status: {}", response.statusCode());
                        return response.bodyToMono(String.class);
                    })
                    .block();
            
            if (rawResponse != null && !rawResponse.isEmpty()) {
                logger.info("Received response of length: {}", rawResponse.length());
                
                // Log the first part of the response to help with debugging
                logger.debug("Raw response (first 200 chars): {}", 
                    rawResponse.length() > 200 ? rawResponse.substring(0, 200) + "..." : rawResponse);
                
                if (rawResponse.trim().startsWith("[") && rawResponse.trim().endsWith("]")) {
                    logger.info("Response appears to be a valid JSON array");
                    
                    // Try parsing the response as a JSON object
                    try {
                        // Parse the response into an array of maps
                        Object[] playlistsJson = redirectAwareWebClient.get()
                                .uri(apiUrl)
                                .retrieve()
                                .bodyToMono(Object[].class)
                                .block();
                        
                        if (playlistsJson != null && playlistsJson.length > 0) {
                            logger.info("Successfully parsed JSON array with {} items", playlistsJson.length);
                            
                            // Process each playlist object
                            for (Object obj : playlistsJson) {
                                if (obj instanceof Map) {
                                    Map<String, Object> playlist = (Map<String, Object>) obj;
                                    if (playlist.containsKey("title")) {
                                        String title = (String) playlist.get("title");
                                        
                                        // Add the playlist title directly
                                        playlistNames.add(title);
                                        logger.debug("Added playlist: {}", title);
                                        
                                        // Also convert Weekly Mix to JMS format and add it
                                        if (title.startsWith("Weekly Mix ")) {
                                            convertWeeklyMixToJmsFormat(title, playlistNames);
                                        }
                                    }
                                }
                            }
                            
                            logger.info("Extracted {} playlist names", playlistNames.size());
                        } else {
                            logger.warn("No playlists found in API response or failed to parse JSON");
                        }
                    } catch (Exception e) {
                        logger.warn("Could not parse JSON array response: {}", e.getMessage());
                        
                        // As a fallback, try to manually parse the JSON using string operations
                        Pattern titlePattern = Pattern.compile("\"title\":\"([^\"]+)\"");
                        Matcher matcher = titlePattern.matcher(rawResponse);
                        int count = 0;
                        while (matcher.find()) {
                            String title = matcher.group(1);
                            playlistNames.add(title);
                            logger.debug("Manually parsed playlist title: {}", title);
                            count++;
                            
                            // Convert "Weekly Mix" format to "JMS" format
                            if (title.startsWith("Weekly Mix ")) {
                                convertWeeklyMixToJmsFormat(title, playlistNames);
                            }
                        }
                        logger.info("Manually extracted {} playlist titles from JSON string", count);
                    }
                } else {
                    logger.warn("Response doesn't appear to be a JSON array, trying manual parsing");
                    // Try to manually parse the JSON using string operations
                    Pattern titlePattern = Pattern.compile("\"title\":\"([^\"]+)\"");
                    Matcher matcher = titlePattern.matcher(rawResponse);
                    int count = 0;
                    while (matcher.find()) {
                        String title = matcher.group(1);
                        playlistNames.add(title);
                        logger.debug("Manually parsed playlist title: {}", title);
                        count++;
                        
                        // Convert "Weekly Mix" format to "JMS" format
                        if (title.startsWith("Weekly Mix ")) {
                            convertWeeklyMixToJmsFormat(title, playlistNames);
                        }
                    }
                    logger.info("Manually extracted {} playlist titles from JSON string", count);
                }
            } else {
                logger.warn("Received empty response from API");
            }
            
        } catch (Exception ex) {
            logger.error("Error fetching playlists from API: {}", ex.getMessage(), ex);
        }
        
        return playlistNames;
    }
    
    /**
     * Fetch from API with more detailed error handling
     */
    private boolean fetchFromApi(Set<String> playlistNames) {
        try {
            logger.info("Fetching existing playlist names from JMS API: {}", jmsApiPlaylistsUrl);
            
            // First try to parse the JSON format we've seen in paste.txt
            try {
                // Make a manual GET request with redirect-following
                String rawResponse = redirectAwareWebClient.get()
                        .uri(jmsApiPlaylistsUrl)
                        .exchangeToMono(response -> {
                            logger.info("Received response with status: {}", response.statusCode());
                            return response.bodyToMono(String.class);
                        })
                        .block();
                
                if (rawResponse != null && !rawResponse.isEmpty()) {
                    logger.info("Successfully received raw API response with length: {}", rawResponse.length());
                    logger.debug("Raw API response (first 200 chars): {}", 
                          rawResponse.length() > 200 ? rawResponse.substring(0, 200) + "..." : rawResponse);
                    
                    // Try to extract playlist titles using regex pattern for the known format
                    if (rawResponse.contains("\"title\":") && 
                        (rawResponse.contains("\"link\":") || rawResponse.contains("\"tag\":"))) {
                        
                        logger.info("Response appears to contain playlist data in expected format, parsing...");
                        Pattern playlistPattern = Pattern.compile("\\{[^\\}]*\"title\"\\s*:\\s*\"([^\"]+)\"[^\\}]*\\}");
                        Matcher matcher = playlistPattern.matcher(rawResponse);
                        
                        int count = 0;
                        while (matcher.find()) {
                            String title = matcher.group(1);
                            playlistNames.add(title);
                            logger.debug("Parsed playlist title: {}", title);
                            count++;
                            
                            // Also handle JMS format conversion
                            if (title.startsWith("Weekly Mix ")) {
                                convertWeeklyMixToJmsFormat(title, playlistNames);
                            }
                        }
                        
                        // If we couldn't find matches with the regex above, try a more direct approach
                        if (count == 0 && rawResponse.contains("\"title\":")) {
                            // Simple pattern to match "title":"..."
                            Pattern simpleTitlePattern = Pattern.compile("\"title\":\"([^\"]+)\"");
                            Matcher simpleMatcher = simpleTitlePattern.matcher(rawResponse);
                            
                            while (simpleMatcher.find()) {
                                String title = simpleMatcher.group(1);
                                playlistNames.add(title);
                                logger.debug("Parsed playlist title using simple pattern: {}", title);
                                count++;
                                
                                // Also handle JMS format conversion
                                if (title.startsWith("Weekly Mix ")) {
                                    convertWeeklyMixToJmsFormat(title, playlistNames);
                                }
                            }
                        }
                        
                        if (count > 0) {
                            logger.info("Successfully extracted {} playlist titles from JSON data", count);
                            return true;
                        }
                    }
                } else {
                    logger.warn("Empty response from JMS API");
                }
            } catch (Exception e) {
                logger.warn("Error trying to parse paste.txt format: {}", e.getMessage());
            }
            
            // Fallback to using our general method
            Set<String> fetchedPlaylists = fetchJmsPlaylists(jmsApiPlaylistsUrl);
            if (!fetchedPlaylists.isEmpty()) {
                logger.info("Successfully fetched {} playlists from JMS API using fetchJmsPlaylists", fetchedPlaylists.size());
                playlistNames.addAll(fetchedPlaylists);
                return true;
            }
            
            return false;
        } catch (Exception ex) {
            logger.error("Unexpected error fetching JMS API: {}", ex.getMessage(), ex);
            return false;
        }
    }
    
    /**
     * Fetch playlist data from the JMS website by scraping HTML
     * @param playlistNames Set to add found playlist names to
     * @return true if website fetch was successful, false otherwise
     */
    private boolean fetchFromWebsite(Set<String> playlistNames) {
        try {
            logger.info("Fetching existing playlist names from JMS website: {}", jmsPlaylistsUrl);
            
            String pageContent = redirectAwareWebClient.get()
                    .uri(jmsPlaylistsUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (pageContent != null && !pageContent.isEmpty()) {
                // Extract playlist names from the page content
                Set<String> websitePlaylistNames = extractPlaylistNamesFromHtml(pageContent);
                logger.info("Found {} existing playlist names on JMS website", websitePlaylistNames.size());
                playlistNames.addAll(websitePlaylistNames);
                return true;
            } else {
                logger.warn("Received empty response from JMS website");
                return false;
            }
        } catch (WebClientResponseException wcre) {
            logger.error("Error fetching JMS website: {} - {}", wcre.getStatusCode(), wcre.getStatusText());
            return false;
        } catch (Exception ex) {
            logger.error("Unexpected error fetching JMS website: {}", ex.getMessage(), ex);
            return false;
        }
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
        
        // Add JMS Mix playlists
        findJmsMixPlaylists(playlistNames, htmlContent, PLAYLIST_NAME_PATTERN_5);
        findJmsMixPlaylists(playlistNames, htmlContent, PLAYLIST_NAME_PATTERN_6);
        
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
    
    /**
     * Find JMS Mix playlists using a specific pattern
     */
    private void findJmsMixPlaylists(Set<String> playlistNames, String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String mixNumber = matcher.group(1);
            String playlistName = "JMS Mix " + mixNumber;
            playlistNames.add(playlistName);
            logger.debug("Found existing JMS Mix playlist: {}", playlistName);
        }
    }
    
    /**
     * Helper method to convert Weekly Mix format to JMS format
     */
    private void convertWeeklyMixToJmsFormat(String weeklyMixTitle, Set<String> playlistNames) {
        String datePart = weeklyMixTitle.substring("Weekly Mix ".length());
        try {
            // Format: DD.MM.YYYY -> JMS DD.MM.YY
            if (datePart.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                String day = datePart.substring(0, 2);
                String month = datePart.substring(3, 5);
                String year = datePart.substring(8, 10); // Last 2 digits
                String jmsTitle = "JMS " + day + "." + month + "." + year;
                playlistNames.add(jmsTitle);
                logger.debug("Added converted JMS version: {}", jmsTitle);
            } else if (datePart.matches("\\d{1,2}\\.\\d{2}\\.\\d{4}")) {
                // Handle single digit day format
                String[] parts = datePart.split("\\.");
                String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String month = parts[1];
                String year = parts[2].substring(2); // Last 2 digits
                String jmsTitle = "JMS " + day + "." + month + "." + year;
                playlistNames.add(jmsTitle);
                logger.debug("Added converted JMS version (single digit day): {}", jmsTitle);
            }
            
            // Handle numeric format (e.g., "Weekly Mix 4.10.2024")
            else if (datePart.matches("\\d{1,2}\\.\\d{2}\\.\\d{4}")) {
                String[] parts = datePart.split("\\.");
                String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String month = parts[1];
                String year = parts[2].substring(2); // Last 2 digits
                String jmsTitle = "JMS " + day + "." + month + "." + year;
                playlistNames.add(jmsTitle);
                logger.debug("Added converted JMS version from numeric format: {}", jmsTitle);
            }
        } catch (Exception ex) {
            logger.warn("Error converting Weekly Mix date format for '{}': {}", weeklyMixTitle, ex.getMessage());
        }
    }
    
    /**
     * Load a hardcoded subset of playlist data as a last resort fallback
     */
    private void loadHardcodedPlaylistData(Set<String> playlistNames) {
        logger.warn("Using hardcoded playlist data as final fallback");
        String hardcodedData = "[{\"id\":44,\"title\":\"Weekly Mix 21.02.2025\"},{\"id\":43,\"title\":\"Weekly Mix 14.02.2025\"},{\"id\":42,\"title\":\"Weekly Mix 31.01.2025\"},{\"id\":2,\"title\":\"JMS Mix 2\"},{\"id\":1,\"title\":\"JMS Mix 1\"}]";
        
        // Use the simple regex pattern to extract titles
        Pattern titlePattern = Pattern.compile("\"title\":\"([^\"]+)\"");
        Matcher matcher = titlePattern.matcher(hardcodedData);
        int count = 0;
        
        while (matcher.find()) {
            String title = matcher.group(1);
            playlistNames.add(title);
            logger.debug("Added hardcoded playlist: {}", title);
            count++;
            
            // Also convert Weekly Mix to JMS format if applicable
            if (title.startsWith("Weekly Mix ")) {
                convertWeeklyMixToJmsFormat(title, playlistNames);
            }
        }
        
        logger.info("Added {} playlists from hardcoded data", count);
    }
    
    /**
     * Fetch the raw JSON for playlists from the JMS website
     * @return JSON string of playlists or null if not available
     */
    public String fetchPlaylistsJson() {
        logger.info("Fetching raw playlists JSON data from JMS API");
        
        try {
            // Try to fetch from the API endpoint
            String apiResponse = redirectAwareWebClient.get()
                    .uri(jmsApiPlaylistsUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (apiResponse != null && !apiResponse.isEmpty() && 
                apiResponse.contains("title") && apiResponse.contains("link")) {
                logger.info("Successfully retrieved JSON data from JMS API, length: {}", apiResponse.length());
                return apiResponse;
            } else {
                logger.warn("Received invalid response from JMS API, trying backup URL");
            }
        } catch (Exception e) {
            logger.warn("Error fetching playlists from JMS API: {}", e.getMessage());
        }
        
        // Try the backup URL as fallback
        try {
            String backupResponse = redirectAwareWebClient.get()
                    .uri(jmsBackupUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (backupResponse != null && !backupResponse.isEmpty() && 
                backupResponse.contains("title") && backupResponse.contains("link")) {
                logger.info("Successfully retrieved JSON data from backup URL, length: {}", backupResponse.length());
                return backupResponse;
            }
        } catch (Exception e) {
            logger.warn("Error fetching playlists from backup URL: {}", e.getMessage());
        }
        
        // If we get here, we couldn't retrieve valid JSON data
        logger.error("Failed to retrieve valid playlist JSON data from any source");
        return null;
    }
}