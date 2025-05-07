package com.jms.spotifyplaylistauth.controller.api;

import com.jms.spotifyplaylistauth.service.SpotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api")
public class ApiCallbackController {
    private static final Logger logger = LoggerFactory.getLogger(ApiCallbackController.class);
    
    private final SpotifyService spotifyService;
    
    @Autowired
    public ApiCallbackController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }
    
    @GetMapping("/callback")
    public String handleApiCallback(
            @RequestParam(name = "access_token", required = false) String accessToken,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            Model model) {
        
        logger.info("Received API callback with access_token present: {}, code present: {}, error present: {}", 
                    (accessToken != null), (code != null), (error != null));
        
        if (error != null && !error.isEmpty()) {
            // Log the error and add it to the model
            logger.error("Error in API callback: {}", error);
            model.addAttribute("error", error);
            return "api/token-handler";
        }
        
        if (accessToken != null && !accessToken.isEmpty()) {
            // Add the access token to the model for the token handler page
            model.addAttribute("accessToken", accessToken);
            logger.info("Access token received, redirecting to token handler");
            return "redirect:/whatsapp/upload?accessToken=" + accessToken;
        } else if (code != null && !code.isEmpty()) {
            // Add the code to the model for the token handler page
            model.addAttribute("code", code);
            logger.info("Auth code received, redirecting to exchange endpoint");
            return "redirect:/spotify/exchange?code=" + code + "&state=redirect_to_home";
        } else {
            // If no token or code is present, send to token handler to show error
            logger.warn("No access token or code received in API callback");
            model.addAttribute("error", "No access token or code received in callback");
            return "api/token-handler";
        }
    }
    
    @GetMapping("/token-handler")
    public String tokenHandler() {
        // This endpoint is used to directly access the token handler page
        return "api/token-handler";
    }
}
