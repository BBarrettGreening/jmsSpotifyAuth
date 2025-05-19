package com.jms.spotifyplaylistauth.controller.api;

import com.jms.spotifyplaylistauth.dto.SpotifyTokenResponse;
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
            @RequestParam(name = "state", required = false) String state,
            Model model) {
        
        logger.info("Received API callback with access_token present: {}, code present: {}, error present: {}, state: {}", 
                    (accessToken != null), (code != null), (error != null), state);
        
        // Default redirect URL if nothing can be extracted from state
        String redirectUrl = "http://localhost:8080/whatsapp/upload";
        boolean shouldRedirectToJms = false;
        
        // Try to parse state parameter to extract redirect URL
        if (state != null && !state.isEmpty()) {
            logger.info("Processing state parameter: {}", state);
            
            // Try to decode base64 state first (new format)
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(state);
                String decodedState = new String(decodedBytes);
                logger.info("Successfully decoded base64 state: {}", decodedState);
                
                // Try to parse as JSON
                try {
                    org.json.JSONObject stateJson = new org.json.JSONObject(decodedState);
                    if (stateJson.has("redirectTo")) {
                        redirectUrl = stateJson.getString("redirectTo");
                        logger.info("Extracted redirect URL from JSON state: {}", redirectUrl);
                    }
                } catch (Exception e) {
                    logger.warn("Could not parse decoded state as JSON: {}", e.getMessage());
                }
            } catch (Exception e) {
                logger.info("State is not base64 encoded, checking other formats");
                
                // Check legacy formats
                // Format: redirect_to_jms:URL
                if (state.contains("redirect_to_jms")) {
                    shouldRedirectToJms = true;
                    logger.info("State contains legacy redirect to JMS instruction: {}", state);
                    
                    // Extract the redirect URL if it's in the format "redirect_to_jms:URL"
                    if (state.contains(":")) {
                        redirectUrl = state.substring(state.indexOf(":") + 1);
                        logger.info("Extracted redirect URL from legacy state: {}", redirectUrl);
                    } else {
                        // Default JMS redirect URL
                        redirectUrl = "http://localhost:8080/whatsapp/upload";
                        logger.info("Using default JMS redirect URL: {}", redirectUrl);
                    }
                }
            }
        }
        
        // Ensure the redirect URL is valid and starts with / if it's a relative URL
        if (redirectUrl != null && !redirectUrl.isEmpty() && !redirectUrl.startsWith("http") && !redirectUrl.startsWith("/")) {
            redirectUrl = "/" + redirectUrl;
        }
        
        // Handle error cases
        if (error != null && !error.isEmpty()) {
            // Log the error and add it to the model
            logger.error("Error in API callback: {}", error);
            
            // Redirect to the extracted URL with error
            logger.info("Redirecting to {} with error: {}", redirectUrl, error);
            return "redirect:" + redirectUrl + "?error=" + error;
        }
        
        // Handle access token case
        if (accessToken != null && !accessToken.isEmpty()) {
            // Redirect to the extracted URL with token
            logger.info("Redirecting to {} with access token", redirectUrl);
            return "redirect:" + redirectUrl + "?access_token=" + accessToken;
        } 
        // Handle authorization code case
        else if (code != null && !code.isEmpty()) {
            logger.info("Authorization code received: {}", code.substring(0, 5) + "...");
            
            logger.info("Exchanging code for token and redirecting to: {}", redirectUrl);
            
            try {
                // Exchange code for token directly
                SpotifyTokenResponse tokenResponse = spotifyService.exchangeCodeForToken(code, state);
                
                if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                    logger.info("Successfully exchanged code for token, redirecting");
                    return "redirect:" + redirectUrl + "?access_token=" + tokenResponse.getAccessToken() + "&success=spotify_connected";
                } else {
                    logger.error("Failed to exchange code for token");
                    return "redirect:" + redirectUrl + "?error=failed_to_exchange_code";
                }
            } catch (Exception e) {
                logger.error("Error exchanging code for token: {}", e.getMessage());
                return "redirect:" + redirectUrl + "?error=token_exchange_error&message=" + e.getMessage();
            }
        } else {
            // If no token or code is present, log an error
            logger.warn("No access token or code received in API callback");
            
            // Redirect to the extracted URL with error
            logger.info("Redirecting to {} with no token/code error", redirectUrl);
            return "redirect:" + redirectUrl + "?error=no_token_or_code";
        }
    }
    
    @GetMapping("/token-handler")
    public String tokenHandler() {
        // This endpoint is used to directly access the token handler page
        return "api/token-handler";
    }
    
    /**
     * A special endpoint to initiate authentication specifically for the JMS website
     */
    @GetMapping("/auth-for-jms")
    public String authForJms(
            @RequestParam(name = "redirect_url", required = false) String redirectUrl,
            Model model) {
        
        // Default to Spring Boot app if no redirect URL is provided
        String effectiveRedirectUrl = (redirectUrl != null && !redirectUrl.isEmpty()) ?
                redirectUrl : "http://localhost:8080/whatsapp/upload";
        
        logger.info("Initiating auth for JMS website, redirect URL: {}", effectiveRedirectUrl);
        
        // Build the Spotify authorization URL with the redirect URL embedded in state
        String authUrl = spotifyService.buildSpotifyAuthorizationUrl(null, effectiveRedirectUrl);
        
        logger.info("Redirecting to Spotify auth URL: {}", authUrl);
        return "redirect:" + authUrl;
    }
}
