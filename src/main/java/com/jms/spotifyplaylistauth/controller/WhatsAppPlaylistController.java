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
    
    @GetMapping("/upload")
    public String showUploadForm(@RequestParam(required = false) String accessToken, Model model) {
        logger.info("Showing upload form with accessToken present: {}", (accessToken != null));
        model.addAttribute("accessToken", accessToken);
        
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                // Get JMS playlists from website
                Set<String> jmsPlaylists = whatsAppPlaylistService.getJmsWebsitePlaylists();
                model.addAttribute("jmsPlaylists", jmsPlaylists);
                
                // Get user profile and playlist information
                Map<String, Object> userProfile = spotifyPlaylistService.getUserProfile(accessToken);
                model.addAttribute("userProfile", userProfile);
                
                List<Map<String, Object>> playlists = spotifyPlaylistService.getUserPlaylists(accessToken);
                model.addAttribute("playlists", playlists);
                
                logger.info("Successfully retrieved user profile and playlists");
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
        
        logger.info("Received WhatsApp chat upload request with file: {}, size: {}", 
                file.getOriginalFilename(), file.getSize());
        
        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a file to upload");
            model.addAttribute("accessToken", accessToken);
            return "whatsapp-upload";
        }
        
        try {
            // Process WhatsApp chat export and create playlists
            logger.info("Processing WhatsApp chat export");
            List<FridayPlaylist> playlists = whatsAppPlaylistService.processWhatsAppChatExport(file, accessToken);
            model.addAttribute("playlists", playlists);
            model.addAttribute("accessToken", accessToken);
            model.addAttribute("jmsChecked", true); // Indicate that JMS website playlists were checked
            
            if (playlists.isEmpty()) {
                logger.info("No Friday playlists found to create");
                model.addAttribute("message", "No new Friday playlists to create. All detected playlists already exist on the JMS website or in your Spotify account.");
                return "whatsapp-results";
            }
            
            // Create playlists in Spotify
            logger.info("Creating {} playlists in Spotify", playlists.size());
            List<Map<String, Object>> createdPlaylists = whatsAppPlaylistService.createSpotifyPlaylists(playlists, accessToken);
            model.addAttribute("createdPlaylists", createdPlaylists);
            
            if (createdPlaylists.isEmpty()) {
                logger.warn("Failed to create playlists in Spotify");
                model.addAttribute("error", "Failed to create playlists in Spotify");
            } else {
                logger.info("Successfully created {} playlists", createdPlaylists.size());
                model.addAttribute("success", true);
                model.addAttribute("message", "Successfully created " + createdPlaylists.size() + " playlists");
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
