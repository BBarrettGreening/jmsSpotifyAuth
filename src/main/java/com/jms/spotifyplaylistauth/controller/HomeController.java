package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.service.SpotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    private final SpotifyService spotifyService;
    
    @Autowired
    public HomeController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "accessToken", required = false) String accessToken,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "success", required = false) Boolean success,
            Model model) {
        
        if (accessToken != null && !accessToken.isEmpty()) {
            model.addAttribute("accessToken", accessToken);
            model.addAttribute("isAuthenticated", true);
        }
        
        if (error != null) {
            model.addAttribute("error", error);
        }
        
        if (success != null && success) {
            model.addAttribute("success", true);
        }
        
        return "index";
    }
    
    @GetMapping("/auth")
    public String standardAuth() {
        // Use the standard auth method that will redirect back to the homepage
        return "redirect:" + spotifyService.buildSpotifyAuthorizationUrl("redirect_to_home");
    }
    
    @GetMapping("/callback")
    public String handleCallback(@RequestParam(required = false) String access_token,
    @RequestParam(required = false) String code,
    @RequestParam(required = false) String error,
                           Model model,
                           RedirectAttributes redirectAttributes) {
    // For the top-level domain callback
    logger.info("Received top-level callback with access_token present: {}, code present: {}, error present: {}", 
                (access_token != null), (code != null), (error != null));
    
    if (error != null && !error.isEmpty()) {
        // Handle error
    redirectAttributes.addAttribute("error", error);
    return "redirect:/";
    }
    
    if (access_token != null && !access_token.isEmpty()) {
    // If we have a token, redirect to WhatsApp upload
        return "redirect:/whatsapp/upload?accessToken=" + access_token;
        } else if (code != null && !code.isEmpty()) {
            // If we have a code, forward to the redirect handler
            return "redirect:/spotify/exchange?code=" + code + "&state=redirect_to_home";
        } else {
            // If we have neither, redirect to home with error
            redirectAttributes.addAttribute("error", "No access token or code received in callback");
            return "redirect:/";
        }
    }
}