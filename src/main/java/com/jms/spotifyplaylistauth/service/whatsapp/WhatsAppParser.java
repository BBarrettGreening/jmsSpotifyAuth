package com.jms.spotifyplaylistauth.service.whatsapp;

import com.jms.spotifyplaylistauth.dto.WhatsAppMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WhatsAppParser {
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppParser.class);
    
    // Multiple patterns to match different WhatsApp message formats
    private static final Pattern MESSAGE_PATTERN_1 = Pattern.compile("\\[(\\d{2}/\\d{2}/\\d{2,4}),\\s(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]\\s([^:]+):\\s(.+)");
    private static final Pattern MESSAGE_PATTERN_2 = Pattern.compile("(\\d{2}/\\d{2}/\\d{2,4}),\\s(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s-\\s([^:]+):\\s(.+)");
    private static final Pattern MESSAGE_PATTERN_3 = Pattern.compile("(\\d{2}/\\d{2}/\\d{4}),\\s(\\d{1,2}:\\d{2})\\s-\\s([^:]+):\\s(.+)");
    
    // Different date formats
    private static final DateTimeFormatter DATE_FORMATTER_1 = DateTimeFormatter.ofPattern("dd/MM/yy, HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER_2 = DateTimeFormatter.ofPattern("dd/MM/yy, HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER_3 = DateTimeFormatter.ofPattern("MM/dd/yy, HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER_4 = DateTimeFormatter.ofPattern("MM/dd/yy, HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER_5 = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER_6 = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER_7 = DateTimeFormatter.ofPattern("MM/dd/yyyy, HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER_8 = DateTimeFormatter.ofPattern("MM/dd/yyyy, HH:mm");

    public List<WhatsAppMessage> parseWhatsAppChatExport(MultipartFile file) throws IOException {
        List<WhatsAppMessage> messages = new ArrayList<>();
        int lineCount = 0;
        int parsedCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                WhatsAppMessage message = parseMessageLine(line);
                if (message != null) {
                    messages.add(message);
                    parsedCount++;
                }
            }
        }
        
        logger.info("Processed {} lines, parsed {} messages from WhatsApp chat export", lineCount, parsedCount);
        return messages;
    }
    
    private WhatsAppMessage parseMessageLine(String line) {
        // Try pattern 1 (with brackets)
        Matcher matcher = MESSAGE_PATTERN_1.matcher(line);
        if (!matcher.find()) {
            // Try pattern 2 (without brackets)
            matcher = MESSAGE_PATTERN_2.matcher(line);
            if (!matcher.find()) {
                // Try pattern 3 (another format)
                matcher = MESSAGE_PATTERN_3.matcher(line);
                if (!matcher.find()) {
                    return null;
                }
            }
        }
        
        try {
            String dateStr = matcher.group(1);
            String timeStr = matcher.group(2);
            String dateTimeStr = dateStr + ", " + timeStr;
            
            // Try different date formats
            LocalDateTime timestamp = tryParseDateTime(dateTimeStr);
            if (timestamp == null) {
                logger.warn("Failed to parse date/time from line: {}", line);
                return null;
            }
            
            String sender = matcher.group(3).trim();
            String content = matcher.group(4).trim();
            
            logger.debug("Parsed message: date={}, sender={}, content={}", timestamp, sender, 
                    content.length() > 30 ? content.substring(0, 30) + "..." : content);
            
            return new WhatsAppMessage(timestamp, sender, content);
        } catch (Exception e) {
            logger.warn("Error parsing message line: {}", line, e);
            return null;
        }
    }
    
    private LocalDateTime tryParseDateTime(String dateTimeStr) {
        // Try all date formats
        DateTimeFormatter[] formatters = {
            DATE_FORMATTER_1, DATE_FORMATTER_2, DATE_FORMATTER_3, DATE_FORMATTER_4,
            DATE_FORMATTER_5, DATE_FORMATTER_6, DATE_FORMATTER_7, DATE_FORMATTER_8
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                // Continue with next formatter
            }
        }
        
        logger.warn("Could not parse date/time string: {}", dateTimeStr);
        return null;
    }
    
    public List<WhatsAppMessage> filterFridaySpotifyMessages(List<WhatsAppMessage> messages) {
        List<WhatsAppMessage> fridaySpotifyMessages = new ArrayList<>();
        
        for (WhatsAppMessage message : messages) {
            if (message.isFriday() && message.hasSpotifyLink()) {
                fridaySpotifyMessages.add(message);
                logger.debug("Found Friday Spotify message: {}", message);
            }
        }
        
        logger.info("Found {} messages with Spotify links sent on Fridays", fridaySpotifyMessages.size());
        return fridaySpotifyMessages;
    }
}
