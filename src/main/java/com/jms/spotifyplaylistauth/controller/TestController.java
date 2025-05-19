package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.service.JmsWebsiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/test")
@Profile({"dev", "test"})
public class TestController {
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    private final WebClient webClient;
    private final JmsWebsiteService jmsWebsiteService;
    
    @Autowired
    public TestController(WebClient webClient, JmsWebsiteService jmsWebsiteService) {
        this.webClient = webClient;
        this.jmsWebsiteService = jmsWebsiteService;
    }
    
    @GetMapping("/jms-api")
    public String testJmsApi(
            @RequestParam(name = "apiUrl", required = false) String apiUrl,
            Model model) {
        
        // Set default API URL if not provided
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://www.jurassicmusicsociety.com/api/playlists";
        }
        
        model.addAttribute("apiUrl", apiUrl);
        
        try {
            logger.info("Testing JMS API connection to: {}", apiUrl);
            
            // Fetch data from the API
            Object[] playlistsJson = webClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .bodyToMono(Object[].class)
                    .block();
            
            if (playlistsJson == null || playlistsJson.length == 0) {
                model.addAttribute("error", "API responded with empty data");
                return "api-test";
            }
            
            // Extract raw data for displaying
            StringBuilder rawDataBuilder = new StringBuilder();
            int maxItems = Math.min(3, playlistsJson.length);
            
            for (int i = 0; i < maxItems; i++) {
                rawDataBuilder.append("Item ").append(i + 1).append(":\n");
                rawDataBuilder.append(formatJsonObject(playlistsJson[i])).append("\n\n");
            }
            
            model.addAttribute("rawApiData", rawDataBuilder.toString());
            
            // Get playlists using the service with custom API URL
            Set<String> jmsPlaylists = jmsWebsiteService.fetchJmsPlaylists(apiUrl);
            model.addAttribute("jmsPlaylists", jmsPlaylists);
            logger.info("Retrieved {} playlist names using JmsWebsiteService", jmsPlaylists.size());
            
            // Test format conversion
            Map<String, String> convertedPlaylists = new LinkedHashMap<>();
            
            // Add some test cases
            convertedPlaylists.put("Weekly Mix 21.02.2025", convertWeeklyMixToJmsFormat("Weekly Mix 21.02.2025"));
            convertedPlaylists.put("Weekly Mix 01.01.2024", convertWeeklyMixToJmsFormat("Weekly Mix 01.01.2024"));
            convertedPlaylists.put("Weekly Mix 31.12.2023", convertWeeklyMixToJmsFormat("Weekly Mix 31.12.2023"));
            convertedPlaylists.put("Weekly Mix 30.04.2023", convertWeeklyMixToJmsFormat("Weekly Mix 30.04.2023"));
            convertedPlaylists.put("JMS Mix 1", convertWeeklyMixToJmsFormat("JMS Mix 1"));
            
            // Also add current and recent dates
            LocalDate now = LocalDate.now();
            DateTimeFormatter weeklyFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            
            for (int i = 0; i < 3; i++) {
                LocalDate date = now.minusWeeks(i);
                String weeklyMixTitle = "Weekly Mix " + date.format(weeklyFormatter);
                convertedPlaylists.put(weeklyMixTitle, convertWeeklyMixToJmsFormat(weeklyMixTitle));
            }
            
            model.addAttribute("convertedPlaylists", convertedPlaylists);
            model.addAttribute("success", "Successfully connected to JMS API.");
            
        } catch (WebClientResponseException e) {
            logger.error("Error connecting to JMS API: {}", e.getMessage());
            model.addAttribute("error", "Error connecting to API: " + e.getStatusCode() + " - " + e.getStatusText());
        } catch (Exception e) {
            logger.error("Unexpected error testing JMS API: {}", e.getMessage(), e);
            model.addAttribute("error", "Unexpected error: " + e.getMessage());
        }
        
        return "api-test";
    }
    
    private String formatJsonObject(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder();
            
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ");
                
                if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                    sb.append("[complex object]");
                } else {
                    sb.append(entry.getValue());
                }
                
                sb.append("\n");
            }
            
            return sb.toString();
        }
        
        return obj.toString();
    }
    
    private String convertWeeklyMixToJmsFormat(String weeklyMixTitle) {
        if (weeklyMixTitle == null) return null;
        
        // If it's already a JMS Mix title, return it as is
        if (weeklyMixTitle.startsWith("JMS Mix ")) {
            return weeklyMixTitle;
        }
        
        // Convert Weekly Mix title to JMS format
        if (weeklyMixTitle.startsWith("Weekly Mix ")) {
            String datePart = weeklyMixTitle.substring("Weekly Mix ".length());
            
            // Format: DD.MM.YYYY -> DD.MM.YY
            if (datePart.length() >= 10) {
                // Take last 2 chars of year (e.g., 2025 -> 25)
                String shortYear = datePart.substring(8, 10);
                return "JMS " + datePart.substring(0, 6) + shortYear;
            } else {
                // If format is different, use as is
                return "JMS " + datePart;
            }
        }
        
        // If it's not a recognized format, return as is
        return weeklyMixTitle;
    }
}
