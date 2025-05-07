/**
 * Token Parser
 * 
 * This script extracts access tokens from various sources and handles redirects
 * for the Spotify OAuth flow.
 */

/**
 * Try to extract an access token from the page content or URL parameters
 */
function extractAccessToken() {
    console.log('Attempting to extract access token...');
    
    // First check URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    const accessToken = urlParams.get('access_token');
    
    if (accessToken) {
        console.log('Found access token in URL parameters');
        redirectWithToken(accessToken);
        return true;
    }
    
    // Next, try to parse JSON from the page content
    try {
        const pageContent = document.body.innerText || document.body.textContent;
        
        // Look for JSON structure
        if (pageContent.includes('{') && pageContent.includes('}')) {
            // Find the first JSON-like structure in the page
            const jsonStart = pageContent.indexOf('{');
            const jsonEnd = pageContent.lastIndexOf('}') + 1;
            const jsonText = pageContent.substring(jsonStart, jsonEnd);
            
            // Try to parse it
            const tokenData = JSON.parse(jsonText);
            
            if (tokenData && tokenData.access_token) {
                console.log('Found access token in page content');
                redirectWithToken(tokenData.access_token);
                return true;
            } else {
                console.warn('JSON found but no access_token property');
            }
        } else {
            console.warn('No JSON structure found in page content');
        }
    } catch (e) {
        console.error('Error parsing page content:', e);
    }
    
    return false;
}

/**
 * Redirect to the WhatsApp upload page with the token
 */
function redirectWithToken(token) {
    window.location.href = '/whatsapp/upload?accessToken=' + encodeURIComponent(token);
}

/**
 * Process manually entered JSON
 */
function processManualJson() {
    const jsonInput = document.getElementById('json-input');
    if (!jsonInput) {
        console.error('JSON input element not found');
        return;
    }
    
    try {
        const jsonText = jsonInput.value;
        const tokenData = JSON.parse(jsonText);
        
        if (tokenData && tokenData.access_token) {
            redirectWithToken(tokenData.access_token);
        } else {
            showError("The JSON doesn't contain an access_token property");
        }
    } catch (e) {
        showError("Invalid JSON: " + e.message);
    }
}

/**
 * Show an error message
 */
function showError(message) {
    const errorElement = document.getElementById('error-message');
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    } else {
        console.error('Error element not found');
        alert('Error: ' + message);
    }
    
    // Hide spinner if present
    const spinner = document.getElementById('spinner');
    if (spinner) {
        spinner.style.display = 'none';
    }
    
    // Show manual options if present
    const manualOptions = document.getElementById('manual-options');
    if (manualOptions) {
        manualOptions.style.display = 'block';
    }
}

// When the page loads, try to extract the token
window.addEventListener('DOMContentLoaded', function() {
    console.log('DOM loaded, attempting to extract token...');
    
    // Set a short timeout to ensure page is fully rendered
    setTimeout(function() {
        const success = extractAccessToken();
        
        if (!success) {
            console.warn('Failed to extract access token automatically');
            showError('Could not automatically extract access token');
        }
    }, 500);
});