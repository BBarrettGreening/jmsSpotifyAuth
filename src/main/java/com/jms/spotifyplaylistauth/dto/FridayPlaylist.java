package com.jms.spotifyplaylistauth.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FridayPlaylist {
    private LocalDateTime date;
    private String name;
    private List<String> trackUris;
    
    private static final DateTimeFormatter PLAYLIST_NAME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");

    public FridayPlaylist(LocalDateTime date) {
        this.date = date;
        this.name = generatePlaylistName(date);
        this.trackUris = new ArrayList<>();
    }
    
    private String generatePlaylistName(LocalDateTime date) {
        return "JMS " + date.format(PLAYLIST_NAME_FORMATTER);
    }
    
    public void addTrackUri(String trackUri) {
        if (trackUri != null && !trackUris.contains(trackUri)) {
            trackUris.add(trackUri);
        }
    }
    
    public LocalDateTime getDate() {
        return date;
    }
    
    public String getName() {
        return name;
    }
    
    public List<String> getTrackUris() {
        return trackUris;
    }
    
    public boolean hasTrack(String trackUri) {
        return trackUris.contains(trackUri);
    }
    
    public int getTrackCount() {
        return trackUris.size();
    }
    
    @Override
    public String toString() {
        return "FridayPlaylist{" +
                "date=" + date +
                ", name='" + name + '\'' +
                ", trackCount=" + trackUris.size() +
                '}';
    }
}
