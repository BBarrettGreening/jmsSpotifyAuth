package com.jms.spotifyplaylistauth.service.whatsapp;

import com.jms.spotifyplaylistauth.dto.WhatsAppMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WhatsAppParser {
    private static final Logger logger = LoggerFactory.getLogger(WhatsAppParser.class);

    // Updated regex to handle multiple date formats found in WhatsApp exports
    // Handles formats like: "2/16/24, 07:44 - +44 7999 431711: message"
    // and: "[04.05.24, 15:22] Name: message"
    private static final Pattern MESSAGE_PATTERN = Pattern.compile(
            "^\\[?(\\d{1,2}[/.]\\d{1,2}[/.]\\d{2,4}),?\\s(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]?\\s-?\\s*([^:]+):\\s(.+)",
            Pattern.DOTALL
    );

    // Flexible date formatter that can parse multiple date patterns
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ofPattern("M/d/yy"))      // 2/16/24
            .appendOptional(DateTimeFormatter.ofPattern("MM/dd/yy"))    // 02/16/24
            .appendOptional(DateTimeFormatter.ofPattern("d/M/yy"))      // 16/2/24
            .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yy"))    // 16/02/24
            .appendOptional(DateTimeFormatter.ofPattern("dd.MM.yy"))    // 16.02.24
            .appendOptional(DateTimeFormatter.ofPattern("d.M.yy"))      // 16.2.24
            .appendOptional(DateTimeFormatter.ofPattern("M/d/yyyy"))    // 2/16/2024
            .appendOptional(DateTimeFormatter.ofPattern("MM/dd/yyyy"))  // 02/16/2024
            .appendOptional(DateTimeFormatter.ofPattern("d/M/yyyy"))    // 16/2/2024
            .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy"))  // 16/02/2024
            .appendOptional(DateTimeFormatter.ofPattern("dd.MM.yyyy"))  // 16.02.2024
            .appendOptional(DateTimeFormatter.ofPattern("d.M.yyyy"))    // 16.2.2024
            .toFormatter();

    /**
     * Parses WhatsApp chat export handling multi-line messages by reading in blocks
     */
    public List<WhatsAppMessage> parseWhatsAppChatExport(MultipartFile file) throws IOException {
        List<WhatsAppMessage> messages = new ArrayList<>();
        int lineCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder messageBlock = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Check if this line starts a new message
                if (isNewMessage(line)) {
                    // If we have a previous message block, parse it
                    if (messageBlock.length() > 0) {
                        WhatsAppMessage message = parseMessageBlock(messageBlock.toString());
                        if (message != null) {
                            messages.add(message);
                        }
                        messageBlock.setLength(0); // Reset for new message
                    }
                }
                
                // Add the current line to the message block
                messageBlock.append(line);
                if (!line.isEmpty()) {
                    messageBlock.append(System.lineSeparator());
                }
            }

            // Parse the final message block
            if (messageBlock.length() > 0) {
                WhatsAppMessage message = parseMessageBlock(messageBlock.toString());
                if (message != null) {
                    messages.add(message);
                }
            }
        }

        logger.info("Processed {} lines and parsed {} messages from WhatsApp chat export", lineCount, messages.size());
        return messages;
    }

    /**
     * Checks if a line starts a new WhatsApp message
     */
    private boolean isNewMessage(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        return MESSAGE_PATTERN.matcher(line).find();
    }

    /**
     * Parses a complete message block (potentially multi-line)
     */
    private WhatsAppMessage parseMessageBlock(String block) {
        if (block == null || block.trim().isEmpty()) {
            return null;
        }

        String trimmedBlock = block.trim();
        Matcher matcher = MESSAGE_PATTERN.matcher(trimmedBlock);
        
        if (matcher.find()) {
            try {
                String dateStr = matcher.group(1);
                String timeStr = matcher.group(2);
                String author = matcher.group(3).trim();
                String content = matcher.group(4).trim();

                // Parse the date using flexible formatter
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                
                // Create a LocalDateTime with the parsed date (time not crucial for Friday filtering)
                LocalDateTime timestamp = date.atStartOfDay();

                logger.debug("Parsed message: Date={}, Author={}, Content length={}", 
                           date, author, content.length());

                return new WhatsAppMessage(timestamp, author, content);
                
            } catch (DateTimeParseException e) {
                logger.warn("Could not parse date from message block: {}", 
                          trimmedBlock.substring(0, Math.min(100, trimmedBlock.length())), e);
                return null;
            } catch (Exception e) {
                logger.warn("Error parsing message block: {}", 
                          trimmedBlock.substring(0, Math.min(100, trimmedBlock.length())), e);
                return null;
            }
        } else {
            logger.debug("Message block did not match pattern: {}", 
                       trimmedBlock.substring(0, Math.min(50, trimmedBlock.length())));
            return null;
        }
    }

    /**
     * Filters messages to find those sent on Friday that contain Spotify links
     */
    public List<WhatsAppMessage> filterFridaySpotifyMessages(List<WhatsAppMessage> messages) {
        List<WhatsAppMessage> fridaySpotifyMessages = new ArrayList<>();

        for (WhatsAppMessage message : messages) {
            if (message.isFriday() && message.hasSpotifyLink()) {
                fridaySpotifyMessages.add(message);
                logger.debug("Found Friday Spotify message: Date={}, Author={}, Content={}", 
                           message.getTimestamp().toLocalDate(), 
                           message.getAuthor(), 
                           message.getContent().substring(0, Math.min(100, message.getContent().length())));
            }
        }

        logger.info("Found {} messages with Spotify links sent on Fridays", fridaySpotifyMessages.size());
        return fridaySpotifyMessages;
    }
}
