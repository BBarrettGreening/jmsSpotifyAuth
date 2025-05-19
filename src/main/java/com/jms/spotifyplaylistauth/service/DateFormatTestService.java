package com.jms.spotifyplaylistauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A service to test the JMS playlist date format conversion
 * This will only run when the 'test-date-format' profile is active
 */
@Service
@Profile("test-date-format")
public class DateFormatTestService implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DateFormatTestService.class);
    
    @Override
    public void run(String... args) {
        logger.info("Testing JMS playlist date format conversion");
        
        // Test various formats of "Weekly Mix" titles to ensure they convert correctly to "JMS" format
        Map<String, String> testCases = new LinkedHashMap<>();
        testCases.put("Weekly Mix 21.02.2025", "JMS 21.02.25");
        testCases.put("Weekly Mix 01.01.2024", "JMS 01.01.24");
        testCases.put("Weekly Mix 31.12.2023", "JMS 31.12.23");
        testCases.put("Weekly Mix 05.05.2024", "JMS 05.05.24");
        testCases.put("Weekly Mix 21.02.25", "JMS 21.02.25");
        testCases.put("JMS Mix 1", "JMS Mix 1");
        
        // Try converting each test case
        for (Map.Entry<String, String> testCase : testCases.entrySet()) {
            String input = testCase.getKey();
            String expectedOutput = testCase.getValue();
            String actualOutput = convertWeeklyMixToJmsFormat(input);
            
            boolean passed = expectedOutput.equals(actualOutput);
            logger.info("Test case: {} -> {} (expected: {}) - {}", 
                    input, actualOutput, expectedOutput, passed ? "PASSED" : "FAILED");
        }
        
        // Also try creating some JMS playlists from dates
        logger.info("Testing JMS playlist date generation from LocalDate");
        LocalDate now = LocalDate.now();
        
        for (int i = 0; i < 5; i++) {
            LocalDate date = now.minusWeeks(i);
            String jmsPlaylistName = createJmsPlaylistName(date);
            logger.info("Date {} -> JMS playlist name: {}", date, jmsPlaylistName);
        }
    }
    
    private String convertWeeklyMixToJmsFormat(String weeklyMixTitle) {
        if (weeklyMixTitle == null) return null;
        
        // If it's already a JMS Mix title, return it as is
        if (weeklyMixTitle.startsWith("JMS Mix ")) {
            return weeklyMixTitle;
        }
        
        // Convert Weekly Mix title to JMS format
        if (weeklyMixTitle.startsWith("Weekly Mix ")) {
            String datePart = weeklyMixTitle.substring("Weekly Mix ".length());
            
            // Format: DD.MM.YYYY -> DD.MM.YY
            if (datePart.length() >= 10) {
                // Take last 2 chars of year (e.g., 2025 -> 25)
                String shortYear = datePart.substring(8, 10);
                return "JMS " + datePart.substring(0, 6) + shortYear;
            } else {
                // If format is different, use as is
                return "JMS " + datePart;
            }
        }
        
        // If it's not a recognized format, return as is
        return weeklyMixTitle;
    }
    
    private String createJmsPlaylistName(LocalDate date) {
        DateTimeFormatter jmsFormatter = DateTimeFormatter.ofPattern("dd.MM.yy");
        return "JMS " + date.format(jmsFormatter);
    }
}
