package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.dto.SpotifyTokenResponse;
import com.jms.spotifyplaylistauth.service.SpotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${jms.redirect-url:http://localhost:8080/whatsapp/upload}")
    private String defaultJmsRedirectUrl;
    
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
    
    @GetMapping("/auth-for-jms")
    public String authForJms(
            @RequestParam(name = "redirect_url", required = false) String redirectUrl) {
        
        // Add the current timestamp to make each state unique
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // Use the provided redirect URL or fall back to the default one
        String finalRedirectUrl = (redirectUrl != null && !redirectUrl.isEmpty()) 
                ? redirectUrl 
                : defaultJmsRedirectUrl;
                
        // Build a state that indicates we should redirect back to JMS
        String state = "redirect_to_jms:" + finalRedirectUrl + "_" + timestamp;
        
        // Build the Spotify authorization URL with appropriate state
        String authUrl = spotifyService.buildSpotifyAuthorizationUrl(state);
        
        logger.info("Initiating auth for JMS website, redirect URL: {}, state: {}", 
                finalRedirectUrl, state);
                
        return "redirect:" + authUrl;
    }
    
    @GetMapping("/callback")
    public String handleCallback(@RequestParam(required = false) String access_token,
    @RequestParam(required = false) String code,
    @RequestParam(required = false) String error,
    @RequestParam(required = false) String state,
    Model model,
    RedirectAttributes redirectAttributes) {
        // For the top-level domain callback
        logger.info("Received top-level callback with access_token present: {}, code present: {}, error present: {}, state: {}", 
                    (access_token != null), (code != null), (error != null), state);
        
        // Check if we need to redirect back to JMS
        boolean shouldRedirectToJms = false;
        String redirectToJms = null;
        
        if (state != null && state.contains("redirect_to_jms")) {
            shouldRedirectToJms = true;
            logger.info("State indicates redirect to JMS: {}", state);
            
            // Extract the redirect URL if provided
            if (state.contains(":")) {
                redirectToJms = state.substring(state.indexOf(":") + 1);
                // Remove timestamp if present
                if (redirectToJms.contains("_")) {
                    redirectToJms = redirectToJms.substring(0, redirectToJms.lastIndexOf("_"));
                }
                logger.info("Extracted JMS redirect URL: {}", redirectToJms);
            }
        }
        
        // Use default JMS redirect if needed
        if (shouldRedirectToJms && (redirectToJms == null || redirectToJms.isEmpty())) {
            redirectToJms = defaultJmsRedirectUrl;
            logger.info("Using default JMS redirect URL: {}", redirectToJms);
        }
        
        if (error != null && !error.isEmpty()) {
            // Handle error
            logger.error("Error in callback: {}", error);
            
            if (shouldRedirectToJms) {
                return "redirect:" + redirectToJms + "?error=" + error;
            }
            
            redirectAttributes.addAttribute("error", error);
            return "redirect:/";
        }
        
        if (access_token != null && !access_token.isEmpty()) {
            // If we have a token
            if (shouldRedirectToJms) {
                return "redirect:" + redirectToJms + "?access_token=" + access_token;
            }
            
            // If we have a token, redirect to WhatsApp upload
            return "redirect:/whatsapp/upload?accessToken=" + access_token;
        } else if (code != null && !code.isEmpty()) {
            // If we have a code
            if (shouldRedirectToJms) {
                // Exchange code and then redirect
                try {
                    logger.info("Exchanging code for token before JMS redirect");
                    SpotifyTokenResponse tokenResponse = spotifyService.exchangeCodeForToken(code, state);
                    if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                        logger.info("Successfully exchanged code for token, redirecting to JMS");
                        return "redirect:" + redirectToJms + "?access_token=" + tokenResponse.getAccessToken();
                    } else {
                        logger.error("Failed to exchange code for token");
                        return "redirect:" + redirectToJms + "?error=token_exchange_failed";
                    }
                } catch (Exception e) {
                    logger.error("Error exchanging code: {}", e.getMessage());
                    return "redirect:" + redirectToJms + "?error=" + e.getMessage();
                }
            }
            
            // If we have a code, forward to the redirect handler
            return "redirect:/spotify/exchange?code=" + code + "&state=redirect_to_home";
        } else {
            // If we have neither, redirect with error
            logger.warn("No access token or code received in callback");
            
            if (shouldRedirectToJms) {
                return "redirect:" + redirectToJms + "?error=no_access_token_or_code";
            }
            
            redirectAttributes.addAttribute("error", "No access token or code received in callback");
            return "redirect:/";
        }
    }
    
    @GetMapping("/jms-test")
    public String jmsTest(
            @RequestParam(name = "access_token", required = false) String accessToken,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "success", required = false) Boolean success,
            Model model) {
        
        if (accessToken != null && !accessToken.isEmpty()) {
            model.addAttribute("accessToken", accessToken);
            model.addAttribute("success", true);
        }
        
        if (error != null) {
            model.addAttribute("error", error);
        }
        
        if (success != null && success) {
            model.addAttribute("success", true);
        }
        
        return "jms-test";
    }
}