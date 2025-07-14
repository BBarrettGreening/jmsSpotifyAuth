package com.jms.spotifyplaylistauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jms.spotifyplaylistauth.dto.FridayPlaylist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SpotifyPlaylistService {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyPlaylistService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private final Map<String, List<Map<String, Object>>> recentlyCreatedPlaylists = new ConcurrentHashMap<>();

    @Autowired
    public SpotifyPlaylistService(WebClient webClient) {
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper();
    }

    public void trackCreatedPlaylist(String userId, Map<String, Object> playlistInfo) {
        logger.info("Tracking newly created playlist for user {}: {}", userId, playlistInfo.get("name"));
        recentlyCreatedPlaylists.computeIfAbsent(userId, k -> new ArrayList<>());
        String newPlaylistId = (String) playlistInfo.get("id");
        if (newPlaylistId != null && recentlyCreatedPlaylists.get(userId).stream().noneMatch(p -> newPlaylistId.equals(p.get("id")))) {
            recentlyCreatedPlaylists.get(userId).add(playlistInfo);
            logger.info("Added playlist to tracking: {}", playlistInfo.get("name"));
        }
    }

    public List<Map<String, Object>> getRecentlyCreatedPlaylists(String userId) {
        return recentlyCreatedPlaylists.getOrDefault(userId, new ArrayList<>());
    }

    public void clearRecentlyCreatedPlaylists(String userId) {
        logger.info("Clearing recently created playlists list for user {}", userId);
        recentlyCreatedPlaylists.remove(userId);
    }

    public List<String> undoRecentPlaylistCreation(String accessToken, String userId) {
        List<Map<String, Object>> playlists = getRecentlyCreatedPlaylists(userId);
        if (playlists == null || playlists.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> undonePlaylistNames = new ArrayList<>();
        List<Map<String, Object>> remainingPlaylists = new ArrayList<>();

        for (Map<String, Object> playlist : playlists) {
            String playlistId = (String) playlist.get("id");
            String playlistName = (String) playlist.get("name");
            if (playlistId != null && !playlistId.isEmpty()) {
                if (deletePlaylist(accessToken, playlistId)) {
                    undonePlaylistNames.add(playlistName);
                } else {
                    remainingPlaylists.add(playlist);
                }
            }
        }

        if (remainingPlaylists.isEmpty()) {
            clearRecentlyCreatedPlaylists(userId);
        } else {
            recentlyCreatedPlaylists.put(userId, remainingPlaylists);
        }
        return undonePlaylistNames;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserPlaylists(String accessToken) {
        List<Map<String, Object>> allPlaylists = new ArrayList<>();
        String nextUrl = "https://api.spotify.com/v1/me/playlists?limit=50";

        try {
            while (nextUrl != null && !nextUrl.isEmpty()) {
                String responseBody = webClient.get()
                        .uri(nextUrl)
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (responseBody == null || responseBody.isEmpty()) {
                    break;
                }

                Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

                if (response != null && response.containsKey("items")) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                    allPlaylists.addAll(items);
                    nextUrl = (String) response.get("next");
                } else {
                    nextUrl = null;
                }
            }
            return allPlaylists;
        } catch (WebClientResponseException | IOException e) {
            logger.error("Error retrieving user playlists: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public Set<String> getUserPlaylistNames(String accessToken) {
        List<Map<String, Object>> playlists = getUserPlaylists(accessToken);
        return playlists.stream()
                .map(playlist -> (String) playlist.get("name"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Map<String, Object> createPlaylist(String accessToken, String userId, String name, String description) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", name);
            requestBody.put("description", description);
            requestBody.put("public", false);

            Map<String, Object> response = webClient.post()
                    .uri("https://api.spotify.com/v1/users/" + userId + "/playlists")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("id")) {
                trackCreatedPlaylist(userId, response);
            }
            return response;
        } catch (WebClientResponseException e) {
            logger.error("Error creating playlist: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    public boolean addTracksToPlaylist(String accessToken, String playlistId, List<String> trackUris) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("uris", trackUris);
            webClient.post()
                    .uri("https://api.spotify.com/v1/playlists/" + playlistId + "/tracks")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            logger.error("Error adding tracks to playlist: {}", e.getMessage(), e);
            return false;
        }
    }

    // ** RESTORED THIS METHOD TO ITS ORIGINAL STATE **
    public Map<String, Object> createPlaylistWithTracks(String accessToken, String userId, FridayPlaylist playlist) {
        logger.info("Creating new playlist '{}' with {} tracks", playlist.getName(), playlist.getTrackUris().size());

        List<Map<String, Object>> existingPlaylists = getUserPlaylists(accessToken);
        String existingPlaylistId = null;

        for (Map<String, Object> existingPlaylist : existingPlaylists) {
            String name = (String) existingPlaylist.get("name");
            if (name != null && name.equals(playlist.getName())) {
                existingPlaylistId = (String) existingPlaylist.get("id");
                logger.info("Found existing playlist with same name: {} ({})", name, existingPlaylistId);
                break;
            }
        }

        Map<String, Object> createdPlaylist;

        if (existingPlaylistId != null) {
            createdPlaylist = new HashMap<>();
            createdPlaylist.put("id", existingPlaylistId);
            createdPlaylist.put("name", playlist.getName());

            if (addTracksToPlaylist(accessToken, existingPlaylistId, playlist.getTrackUris())) {
                trackCreatedPlaylist(userId, createdPlaylist);
            }
        } else {
            createdPlaylist = createPlaylist(
                    accessToken,
                    userId,
                    playlist.getName(),
                    "Friday Spotify links from WhatsApp group chat"
            );

            if (createdPlaylist != null && createdPlaylist.containsKey("id")) {
                String playlistId = (String) createdPlaylist.get("id");
                addTracksToPlaylist(accessToken, playlistId, playlist.getTrackUris());
            } else {
                return Collections.emptyMap();
            }
        }

        return createdPlaylist;
    }

    public Map<String, Object> getUserProfile(String accessToken) {
        try {
            return webClient.get()
                    .uri("https://api.spotify.com/v1/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException e) {
            logger.error("HTTP Error retrieving user profile: {} {}", e.getStatusCode(), e.getStatusText());
            return Collections.emptyMap();
        }
    }

    public boolean deletePlaylist(String accessToken, String playlistId) {
        try {
            webClient.delete()
                    .uri("https://api.spotify.com/v1/playlists/" + playlistId + "/followers")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            logger.error("Error deleting playlist {}: {} {}", playlistId, e.getStatusCode(), e.getStatusText());
            return false;
        }
    }

    public int deletePlaylists(String accessToken, String namePattern) {
        List<Map<String, Object>> playlists = getUserPlaylists(accessToken);
        int deletedCount = 0;
        Pattern pattern = Pattern.compile(namePattern);
        for (Map<String, Object> playlist : playlists) {
            String name = (String) playlist.get("name");
            String id = (String) playlist.get("id");
            if (name != null && pattern.matcher(name).matches() && id != null) {
                if (deletePlaylist(accessToken, id)) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }
}