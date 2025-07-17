package com.jms.spotifyplaylistauth.controller;

import com.jms.spotifyplaylistauth.dto.SpotifyTokenResponse;
import com.jms.spotifyplaylistauth.service.SpotifyService;
import com.jms.spotifyplaylistauth.service.UrlBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
@RequestMapping("/spotify")
public class SpotifyAuthController {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyAuthController.class);

    private final SpotifyService spotifyService;
    private final UrlBuilderService urlBuilderService;

    @Autowired
    public SpotifyAuthController(SpotifyService spotifyService, UrlBuilderService urlBuilderService) {
        this.spotifyService = spotifyService;
        this.urlBuilderService = urlBuilderService;
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(name = "redirect_url", required = false) String redirectUrl,
            @RequestParam(name = "origin", required = false) String origin,
            HttpServletRequest request) {
        
        // Determine the redirect URL to return to after auth
        String effectiveRedirectUrl = null;
        
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            // Use explicitly provided redirect URL
            effectiveRedirectUrl = redirectUrl;
        } else if (origin != null && !origin.isEmpty()) {
            // Use the origin as the redirect URL
            effectiveRedirectUrl = origin;
        } else {
            // Build the redirect URL dynamically using the URL builder service
            effectiveRedirectUrl = urlBuilderService.buildFullUrl(request, "/whatsapp/upload");
        }
        
        logger.info("Spotify login initiated, will return to: {}", effectiveRedirectUrl);
        String authUrl = spotifyService.buildSpotifyAuthorizationUrl(null, effectiveRedirectUrl);
        logger.info("Redirecting to Spotify auth URL: {}", authUrl);
        return "redirect:" + authUrl;
    }
    
    @GetMapping("/redirect")
    public String spotifyRedirect(@RequestParam(required = false) String access_token,
                                 @RequestParam(required = false) String code,
                                 Model model) {
        logger.info("Received redirect with access_token present: {}, code present: {}", 
                    (access_token != null), (code != null));
                    
        // If access_token is available, add it to the model
        if (access_token != null && !access_token.isEmpty()) {
            model.addAttribute("accessToken", access_token);
        }
        
        // If code is available, prepare to exchange it (will be done client-side for simplicity)
        if (code != null && !code.isEmpty()) {
            model.addAttribute("code", code);
        }
        
        // This is a special page that will handle the JSON response from Spotify
        return "spotify-redirect";
    }
    
    @GetMapping("/manual-token")
    public String manualTokenEntryForm() {
        // Show the manual token entry form
        return "manual-token-entry";
    }
    
    @PostMapping("/handle-manual-token")
    public String handleManualToken(
            @RequestParam("accessToken") String accessToken,
            @RequestParam("refreshToken") String refreshToken,
            @RequestParam("tokenType") String tokenType,
            @RequestParam("expiresIn") String expiresIn,
            Model model) {
        
        logger.info("Received manual token entry: accessToken length={}", accessToken.length());
        
        try {
            // Validate token by trying to get user profile
            Map<String, Object> userProfile = spotifyService.getUserProfile(accessToken);
            
            if (userProfile != null && userProfile.containsKey("id")) {
                // Token is valid, proceed with authentication
                model.addAttribute("accessToken", accessToken);
                model.addAttribute("tokenType", tokenType);
                model.addAttribute("expiresIn", expiresIn);
                model.addAttribute("refreshToken", refreshToken);
                model.addAttribute("userProfile", userProfile);
                
                return "auth-success";
            } else {
                model.addAttribute("error", "Invalid access token. Unable to retrieve user profile.");
                return "manual-token-entry";
            }
        } catch (Exception e) {
            logger.error("Error validating manual token: {}", e.getMessage(), e);
            model.addAttribute("error", "Error validating token: " + e.getMessage());
            return "manual-token-entry";
        }
    }

    @GetMapping("/handle-callback")
    public String handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String access_token,
            @RequestParam(required = false) String token_type,
            @RequestParam(required = false) String refresh_token,
            @RequestParam(required = false) String expires_in,
            @RequestParam(required = false) String error,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {
        
        logger.info("Received callback with access_token present: {}, state: {}", (access_token != null), state);
        
        try {
            // Try to extract the original redirect URL from the state
            String originalRedirectUrl = urlBuilderService.extractRedirectUrlFromState(state);
            
            // Also check for the old format
            if (originalRedirectUrl == null && state != null && state.startsWith("redirect_to_jms") && state.contains(":")) {
                originalRedirectUrl = state.substring(state.indexOf(":") + 1);
            }
            
            // Backward compatibility flags
            boolean shouldRedirectToJms = (state != null && state.startsWith("redirect_to_jms"));
            String jmsRedirectUrl = originalRedirectUrl != null ? originalRedirectUrl : null;
            
            // Set a default redirect URL if needed (but prefer dynamic detection)
            if (shouldRedirectToJms && (jmsRedirectUrl == null || jmsRedirectUrl.isEmpty())) {
                jmsRedirectUrl = urlBuilderService.buildFullUrl(request, "/whatsapp/upload");
            }
            
            // Handle error case
            if (error != null) {
                logger.error("Error in Spotify callback: {}", error);
                
                // If we have an original redirect URL, redirect there with error
                if (originalRedirectUrl != null) {
                    logger.info("Redirecting to original URL with error: {}", error);
                    String separator = originalRedirectUrl.contains("?") ? "&" : "?";
                    return "redirect:" + originalRedirectUrl + separator + "error=" + error;
                }
                // If we should redirect to JMS website with error (backward compatibility)
                else if (shouldRedirectToJms) {
                    logger.info("Redirecting to JMS website with error: {}", error);
                    return "redirect:" + jmsRedirectUrl + "?error=" + error;
                }
                
                redirectAttributes.addFlashAttribute("error", "Authentication error: " + error);
                return "redirect:/";
            }
            
            // Handle direct token information
            if (access_token != null) {
                logger.info("Using provided access token");
                
                // Get user profile using the provided access token
                Map<String, Object> userProfile = spotifyService.getUserProfile(access_token);
                
                // If we have an original redirect URL, use it
                if (originalRedirectUrl != null) {
                    logger.info("Redirecting to original URL with access token: {}", originalRedirectUrl);
                    String separator = originalRedirectUrl.contains("?") ? "&" : "?";
                    return "redirect:" + originalRedirectUrl + separator + "access_token=" + access_token;
                }
                // Check if we should redirect to JMS website (backward compatibility)
                else if (shouldRedirectToJms) {
                    logger.info("Redirecting to JMS website with access token");
                    return "redirect:" + jmsRedirectUrl + "?access_token=" + access_token;
                }
                // Check if we should redirect to home page
                else if (state != null && state.equals("redirect_to_home")) {
                    redirectAttributes.addFlashAttribute("successMsg", "Authentication successful!");
                    return "redirect:/?accessToken=" + access_token;
                } else {
                    // By default, redirect to the WhatsApp upload page
                    return "redirect:/whatsapp/upload?accessToken=" + access_token;
                }
            }
            
            // If no direct token but we have a code, exchange it for a token
            if (code != null) {
                logger.info("Exchanging code for token");
                SpotifyTokenResponse tokenResponse = spotifyService.exchangeCodeForToken(code, state);
                
                if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                    // Get user profile
                    Map<String, Object> userProfile = spotifyService.getUserProfile(tokenResponse.getAccessToken());
                    
                    // If we have an original redirect URL, use it
                    if (originalRedirectUrl != null) {
                        logger.info("Redirecting to original URL with access token after exchange: {}", originalRedirectUrl);
                        String separator = originalRedirectUrl.contains("?") ? "&" : "?";
                        return "redirect:" + originalRedirectUrl + separator + "access_token=" + tokenResponse.getAccessToken();
                    }
                    // Check if we should redirect to JMS website (backward compatibility)
                    else if (shouldRedirectToJms) {
                        logger.info("Redirecting to JMS website with access token after exchange");
                        return "redirect:" + jmsRedirectUrl + "?access_token=" + tokenResponse.getAccessToken();
                    }
                    // Check if we should redirect to home page
                    else if (state != null && state.equals("redirect_to_home")) {
                        redirectAttributes.addAttribute("accessToken", tokenResponse.getAccessToken());
                        redirectAttributes.addAttribute("success", true);
                        return "redirect:/";
                    } else {
                        // By default, redirect to the WhatsApp upload page
                        return "redirect:/whatsapp/upload?accessToken=" + tokenResponse.getAccessToken();
                    }
                } else {
                    if (originalRedirectUrl != null) {
                        String separator = originalRedirectUrl.contains("?") ? "&" : "?";
                        return "redirect:" + originalRedirectUrl + separator + "error=failed_to_retrieve_token";
                    } else if (shouldRedirectToJms) {
                        return "redirect:" + jmsRedirectUrl + "?error=failed_to_retrieve_token";
                    }
                    
                    redirectAttributes.addFlashAttribute("error", "Failed to retrieve access token");
                    return "redirect:/";
                }
            }
            
            // If we have neither token nor code
            if (originalRedirectUrl != null) {
                String separator = originalRedirectUrl.contains("?") ? "&" : "?";
                return "redirect:" + originalRedirectUrl + separator + "error=no_token_or_code";
            } else if (shouldRedirectToJms) {
                return "redirect:" + jmsRedirectUrl + "?error=no_token_or_code";
            }
            
            redirectAttributes.addFlashAttribute("error", "No token or authorization code provided");
            return "redirect:/";
            
        } catch (Exception e) {
            logger.error("Authentication error: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Authentication error: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> status(@RequestParam String accessToken) {
        return spotifyService.getUserProfile(accessToken);
    }
    
    @GetMapping("/redirect-to-whatsapp")
    public RedirectView redirectToWhatsApp(@RequestParam String accessToken) {
        return new RedirectView("/whatsapp/upload?accessToken=" + accessToken);
    }
    
    /**
     * Direct endpoint for WhatsApp authentication with Spotify
     * This simplifies the authentication flow for WhatsApp playlist creation
     * The JMS website will receive the authorization code and redirect back to the originating server
     */
    @GetMapping("/whatsapp-auth")
    public String whatsAppDirectAuth(HttpServletRequest request) {
        logger.info("Direct WhatsApp auth initiated");
        
        // Build the return URL dynamically using the URL builder service
        String returnUrl = urlBuilderService.buildFullUrl(request, "/whatsapp/upload");
        
        logger.info("Setting return URL (will be handled by JMS website): {}", returnUrl);
        String authUrl = spotifyService.buildSpotifyAuthorizationUrl(null, returnUrl);
        logger.info("Redirecting to Spotify auth URL with return URL embedded in state");
        logger.debug("Full auth URL: {}", authUrl);
        return "redirect:" + authUrl;
    }
    
    @GetMapping("/exchange")
    public String exchangeCode(
            @RequestParam String code, 
            @RequestParam(required = false) String state,
            RedirectAttributes redirectAttributes) {
        logger.info("Exchanging code for token server-side with state: {}", state);
        
        try {
            SpotifyTokenResponse tokenResponse = spotifyService.exchangeCodeForToken(code, state);
            
            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                // Check if we should redirect to homepage
                if (state != null && state.equals("redirect_to_home")) {
                    redirectAttributes.addAttribute("accessToken", tokenResponse.getAccessToken());
                    redirectAttributes.addAttribute("success", true);
                    return "redirect:/";
                } else {
                    // If we get a token, redirect to WhatsApp upload
                    return "redirect:/whatsapp/upload?accessToken=" + tokenResponse.getAccessToken();
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "Failed to exchange code for token");
                return "redirect:/";
            }
        } catch (Exception e) {
            logger.error("Error exchanging code for token: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error exchanging code for token: " + e.getMessage());
            return "redirect:/";
        }
    }
    
    @GetMapping("/token-handler")
    public String tokenHandler(
            @RequestParam(required = false) String access_token,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            Model model) {
        
        logger.info("Token handler called with access_token present: {}, code present: {}",
                (access_token != null), (code != null));
        
        if (error != null) {
            model.addAttribute("error", error);
            return "token-handler";
        }
        
        if (access_token != null && !access_token.isEmpty()) {
            // If we have a token, redirect to WhatsApp upload
            return "redirect:/whatsapp/upload?accessToken=" + access_token;
        }
        
        // If we have a code, try to exchange it for a token
        if (code != null && !code.isEmpty()) {
            try {
                SpotifyTokenResponse tokenResponse = spotifyService.exchangeCodeForToken(code);
                
                if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                    // If we get a token, redirect to WhatsApp upload
                    return "redirect:/whatsapp/upload?accessToken=" + tokenResponse.getAccessToken();
                }
            } catch (Exception e) {
                logger.error("Error exchanging code for token: {}", e.getMessage(), e);
                model.addAttribute("error", "Error exchanging code for token: " + e.getMessage());
            }
        }
        
        // If we get here, let the page try to extract the token from the response
        return "token-handler";
    }
}
