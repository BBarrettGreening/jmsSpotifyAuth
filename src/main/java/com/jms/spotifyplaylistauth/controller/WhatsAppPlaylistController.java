package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.dto.FridayPlaylist;
import com.jms.spotifyplaylistauth.service.SpotifyPlaylistService;
import com.jms.spotifyplaylistauth.service.WhatsAppPlaylistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/whatsapp")
public class WhatsAppPlaylistController {
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppPlaylistController.class);
    
    private final WhatsAppPlaylistService whatsAppPlaylistService;
    private final SpotifyPlaylistService spotifyPlaylistService;
    
    @Autowired
    public WhatsAppPlaylistController(
            WhatsAppPlaylistService whatsAppPlaylistService,
            SpotifyPlaylistService spotifyPlaylistService) {
        this.whatsAppPlaylistService = whatsAppPlaylistService;
        this.spotifyPlaylistService = spotifyPlaylistService;
    }
    
    @GetMapping("/login")
    public String whatsAppLogin() {
        logger.info("WhatsApp login initiated, using direct WhatsApp auth endpoint");
        return "redirect:/spotify/whatsapp-auth";
    }
    
    @GetMapping("/upload")
    public String showUploadForm(@RequestParam(required = false) String access_token,
                                 @RequestParam(required = false) String accessToken,
                                 @RequestParam(required = false) String error,
                                 @RequestParam(required = false) String success,
                                 Model model) {
        // Handle both access_token and accessToken parameter names for compatibility
        String effectiveToken = access_token != null ? access_token : accessToken;
        
        logger.info("Showing upload form with access_token present: {}, accessToken present: {}, error: {}, success: {}", 
                   (access_token != null), (accessToken != null), error, success);
        
        // Always fetch JMS playlists for the upload form to show the count
        try {
            Set<String> jmsPlaylists = whatsAppPlaylistService.getJmsWebsitePlaylists();
            model.addAttribute("jmsPlaylists", jmsPlaylists);
            model.addAttribute("jmsPlaylistsCount", jmsPlaylists.size());
            logger.info("Added {} JMS playlists to the model for upload form", jmsPlaylists.size());
        } catch (Exception e) {
            logger.error("Error fetching JMS playlists for upload form: {}", e.getMessage(), e);
            model.addAttribute("warning", "Could not fetch JMS website playlists: " + e.getMessage());
        }
        
        if (effectiveToken != null) {
            logger.info("Token is present with length: {}", effectiveToken.length());
        } else {
            logger.warn("No access token found in either parameter");
        }
        
        if (error != null) {
            logger.warn("Error received in upload form: {}", error);
            model.addAttribute("error", "Authentication error: " + error);
        }
        
        if (success != null) {
            logger.info("Success status received: {}", success);
            model.addAttribute("success", true);
        }
        
        // Always use the effective token
        model.addAttribute("accessToken", effectiveToken);
        
        if (effectiveToken != null && !effectiveToken.isEmpty()) {
            try {
                // Get JMS playlists from website
                Set<String> jmsPlaylists = whatsAppPlaylistService.getJmsWebsitePlaylists();
                model.addAttribute("jmsPlaylists", jmsPlaylists);
                
                // Get user profile and playlist information
                logger.info("Fetching user profile with access token");
                Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(effectiveToken);
                
                if (userProfile.isEmpty()) {
                    logger.error("Unable to retrieve user profile - empty response");
                    model.addAttribute("error", "Unable to connect to Spotify. Your access token may have expired. Please log in again.");
                    return "whatsapp-upload";
                }
                
                model.addAttribute("userProfile", userProfile);
                logger.info("User profile retrieved successfully for user {}", userProfile.get("id"));
                
                List<Map<String, Object>> playlists = spotifyPlaylistService.getUserPlaylists(effectiveToken);
                model.addAttribute("playlists", playlists);
                
                logger.info("Successfully retrieved user profile and {} playlists", playlists.size());
            } catch (Exception e) {
                logger.error("Error retrieving user profile or playlists: {}", e.getMessage(), e);
                model.addAttribute("error", "Error retrieving Spotify data: " + e.getMessage());
            }
        }
        
        return "whatsapp-upload";
    }
    
    @PostMapping("/upload")
    public String uploadWhatsAppChat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("accessToken") String accessToken,
            Model model) {
        
        logger.info("Received WhatsApp chat upload request with file: {}, size: {}, accessToken length: {}", 
                file.getOriginalFilename(), file.getSize(), 
                accessToken != null ? accessToken.length() : 0);
        
        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a file to upload");
            // Log the token length for debugging
            logger.info("Setting accessToken in model for results page, length: {}", 
                    accessToken != null ? accessToken.length() : 0);
            model.addAttribute("accessToken", accessToken);
            return "whatsapp-upload";
        }
        
        if (accessToken == null || accessToken.isEmpty()) {
            logger.error("No access token provided. Authentication may have failed or token was lost during redirect.");
            model.addAttribute("error", "No Spotify access token available. Please reconnect with Spotify to continue.");
            return "whatsapp-upload";
        }
        
        try {
            // Process WhatsApp chat export and create playlists
            logger.info("Processing WhatsApp chat export: {}, size: {}", file.getOriginalFilename(), file.getSize());
            
            // Get JMS playlists from website/API
            Set<String> jmsPlaylists;
            try {
                jmsPlaylists = whatsAppPlaylistService.getJmsWebsitePlaylists();
                logger.info("Found {} existing playlists on JMS website/API", jmsPlaylists.size());
                
                // Log all JMS playlists for debugging
                logger.info("All JMS playlists found:");
                for (String playlist : jmsPlaylists) {
                    logger.info("  - JMS Playlist: {}", playlist);
                }
                
                // Always ensure we have model attributes set with real count
                model.addAttribute("jmsPlaylistsCount", jmsPlaylists.size());
            } catch (Exception e) {
                logger.error("Error fetching JMS playlists: {}", e.getMessage(), e);
                // Create an empty set to continue the process
                jmsPlaylists = new HashSet<>();
                model.addAttribute("warning", "Could not fetch JMS website playlists: " + e.getMessage());
                model.addAttribute("jmsPlaylistsCount", 0);
            }
            
            // Get user's Spotify playlists
            Set<String> userPlaylists;
            try {
                userPlaylists = whatsAppPlaylistService.getUserSpotifyPlaylists(accessToken);
                logger.info("Found {} existing playlists in user's Spotify account", userPlaylists.size());
                model.addAttribute("userPlaylistsCount", userPlaylists.size());
            } catch (Exception e) {
                logger.error("Error fetching user's Spotify playlists: {}", e.getMessage(), e);
                // Create an empty set to continue the process
                userPlaylists = new HashSet<>();
                model.addAttribute("warning", (model.getAttribute("warning") != null ? model.getAttribute("warning") + ". " : "") + 
                                   "Could not fetch Spotify playlists: " + e.getMessage());
                model.addAttribute("userPlaylistsCount", 0);
            }
            
            // Log sample JMS playlists for debugging
            int count = 0;
            for (String playlist : jmsPlaylists) {
                if (count < 5 && playlist.startsWith("JMS ")) {
                    logger.debug("Sample JMS playlist: {}", playlist);
                    count++;
                }
            }
            
            // Log sample user playlists for debugging
            count = 0;
            for (String playlist : userPlaylists) {
                if (count < 5 && playlist.startsWith("JMS ")) {
                    logger.debug("Sample user Spotify playlist: {}", playlist);
                    count++;
                }
            }
            
            List<FridayPlaylist> playlists = whatsAppPlaylistService.processWhatsAppChatExport(file, accessToken);
            model.addAttribute("playlists", playlists);
            model.addAttribute("accessToken", accessToken);
            model.addAttribute("jmsChecked", true); // Indicate that JMS website playlists were checked
            model.addAttribute("jmsPlaylistsCount", jmsPlaylists.size());
            model.addAttribute("userPlaylistsCount", userPlaylists.size());
            
            if (playlists.isEmpty()) {
                logger.info("No new Friday playlists found to create");
                model.addAttribute("message", "No new Friday playlists to create. All detected playlists already exist on the JMS website or in your Spotify account.");
                return "whatsapp-results";
            }
            
            // Process WhatsApp chat export and create playlists
            logger.info("Creating {} playlists in Spotify with access token length: {}", playlists.size(), 
                    accessToken != null ? accessToken.length() : 0);
            
            List<Map<String, Object>> createdPlaylists;
            try {
                createdPlaylists = whatsAppPlaylistService.createSpotifyPlaylists(playlists, accessToken);
                model.addAttribute("createdPlaylists", createdPlaylists);
                
                if (createdPlaylists.isEmpty() && !playlists.isEmpty()) {
                    logger.warn("Failed to create playlists in Spotify even though playlists were found");
                    model.addAttribute("error", "Could not create playlists in Spotify. Your access token may be invalid or expired. Please try logging in again.");
                } else if (createdPlaylists.isEmpty()) {
                    logger.info("No playlists were created (either none found or all already exist)");
                    model.addAttribute("success", true);
                    model.addAttribute("message", "No new playlists needed to be created. All detected playlists already exist.");
                } else {
                    logger.info("Successfully created {} playlists", createdPlaylists.size());
                    model.addAttribute("success", true);
                    model.addAttribute("message", String.format(
                        "Successfully created %d playlists that didn't already exist on the JMS website (%d playlists) or in your Spotify account (%d playlists).",
                        createdPlaylists.size(),
                        jmsPlaylists.size(),
                        userPlaylists.size()
                    ));
                }
            } catch (IllegalArgumentException e) {
                logger.error("Error creating playlists - invalid argument: {}", e.getMessage());
                model.addAttribute("error", "Error creating playlists: " + e.getMessage() + 
                                  ". Please try logging in again.");
            } catch (RuntimeException e) {
                logger.error("Error creating playlists - runtime exception: {}", e.getMessage());
                model.addAttribute("error", "Error creating playlists: " + e.getMessage() + 
                                  ". Your Spotify session may have expired. Please try logging in again.");
            } catch (Exception e) {
                logger.error("Unexpected error creating playlists: {}", e.getMessage(), e);
                model.addAttribute("error", "Unexpected error creating playlists: " + e.getMessage());
            }
        } catch (IOException e) {
            logger.error("IOException processing WhatsApp chat export: {}", e.getMessage(), e);
            model.addAttribute("error", "Error reading file: " + e.getMessage());
            model.addAttribute("accessToken", accessToken);
            return "whatsapp-upload";
        } catch (Exception e) {
            logger.error("Exception processing WhatsApp chat export: {}", e.getMessage(), e);
            model.addAttribute("error", "Error processing file: " + e.getMessage());
            model.addAttribute("accessToken", accessToken);
            return "whatsapp-upload";
        }
        
        return "whatsapp-results";
    }
    
    @GetMapping("/analyze")
    @ResponseBody
    public Map<String, Object> analyzeWhatsAppChat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("accessToken") String accessToken) throws IOException {
        
        logger.info("Analyzing WhatsApp chat: {}", file.getOriginalFilename());
        return whatsAppPlaylistService.processAndCreatePlaylists(file, accessToken);
    }
}
