package com.tejastomar.journalapp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tejastomar.journalapp.dto.AIAnalyticsInsightResponseDTO;
import com.tejastomar.journalapp.entity.AIAnalyticsInsight;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.repository.AIAnalyticsInsightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class AIAnalyticsInsightService {

    private static final int ANALYSIS_WINDOW_DAYS = 30;
    private static final int MAX_TITLES_IN_PROMPT = 20;

    @Autowired
    private UserService userService;

    @Autowired
    private AIAnalyticsInsightRepository aiAnalyticsInsightRepository;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private AIConfigurationService aiConfigurationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates a privacy-aware aggregate insight from the last 30 days. This
     * sends metadata only; journal content remains exclusive to per-journal AI.
     */
    public AIAnalyticsInsightResponseDTO generateRecentInsight(String username) {
        User user = getUser(username);
        verifyAIReflectionEnabled(user);

        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(ANALYSIS_WINDOW_DAYS - 1L);
        List<JournalEntry> entries = getEntriesForPeriod(user, periodStart, periodEnd);
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one journal entry is required to generate an AI analytics insight");
        }

        AIAnalyticsInsight existing = aiAnalyticsInsightRepository
                .findFirstByUsernameAndPeriodStartAndPeriodEndAndSourceEntryCount(
                        username, periodStart, periodEnd, entries.size());
        if (existing != null) {
            return toResponseDTO(existing);
        }

        String rawResponse = callOpenAI(buildAnalyticsPrompt(entries, periodStart, periodEnd));
        AIAnalyticsInsight insight = parseInsight(rawResponse, username, periodStart, periodEnd, entries.size());
        return toResponseDTO(aiAnalyticsInsightRepository.save(insight));
    }

    /**
     * Analytics must still work for users who have not opted into AI, so this
     * method returns null rather than throwing when the global setting is off.
     */
    public AIAnalyticsInsightResponseDTO getLatestInsightIfEnabled(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getAiReflectionEnabled())) {
            return null;
        }

        AIAnalyticsInsight insight = aiAnalyticsInsightRepository
                .findFirstByUsernameOrderByGeneratedAtDesc(user.getUserName());
        return insight == null ? null : toResponseDTO(insight);
    }

    private User getUser(String username) {
        User user = userService.findByUserName(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private void verifyAIReflectionEnabled(User user) {
        if (!Boolean.TRUE.equals(user.getAiReflectionEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AI reflection is disabled in account settings");
        }
    }

    private List<JournalEntry> getEntriesForPeriod(User user, LocalDate periodStart, LocalDate periodEnd) {
        List<JournalEntry> entries = user.getJournalEntries();
        if (entries == null) {
            return List.of();
        }

        List<JournalEntry> periodEntries = new ArrayList<>();
        for (JournalEntry entry : entries) {
            if (isWithinPeriod(entry, periodStart, periodEnd)) {
                periodEntries.add(entry);
            }
        }
        periodEntries.sort(Comparator.comparing(JournalEntry::getDate).reversed());
        return periodEntries;
    }

    private boolean isWithinPeriod(JournalEntry entry, LocalDate periodStart, LocalDate periodEnd) {
        if (entry == null || entry.getDate() == null) {
            return false;
        }
        LocalDate entryDate = entry.getDate().toLocalDate();
        return !entryDate.isBefore(periodStart) && !entryDate.isAfter(periodEnd);
    }

    private String buildAnalyticsPrompt(List<JournalEntry> entries, LocalDate periodStart, LocalDate periodEnd) {
        Map<Sentiment, Integer> sentimentBreakdown = calculateSentimentBreakdown(entries);
        int journalDays = getUniqueJournalDays(entries).size();
        int elapsedDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        double consistency = roundToTwoDecimalPlaces((journalDays / (double) elapsedDays) * 100);

        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a supportive, non-medical reflection over journal metadata for ")
                .append(periodStart).append(" through ").append(periodEnd).append(".\n")
                .append("Do not diagnose, do not claim certainty, and do not give therapy or medical advice. ")
                .append("Use careful language such as 'may suggest' or 'appears'.\n")
                .append("Return JSON only with these fields: summary, detectedSentiment, positiveObservation, reflectionQuestion, encouragement. ")
                .append("Use summary for one concise pattern insight (maximum 80 words).\n\n")
                .append("Aggregate metrics:\n")
                .append("- Entries: ").append(entries.size()).append("\n")
                .append("- Unique journaling days: ").append(journalDays).append(" of ").append(elapsedDays).append("\n")
                .append("- Consistency: ").append(consistency).append("%\n")
                .append("- Sentiment counts: ").append(sentimentBreakdown).append("\n")
                .append("- Dominant sentiment: ").append(calculateDominantSentiment(sentimentBreakdown)).append("\n\n")
                .append("Journal metadata only (titles, selected sentiments, and reflection prompts; no journal content):\n");

        int titleCount = Math.min(entries.size(), MAX_TITLES_IN_PROMPT);
        for (int index = 0; index < titleCount; index++) {
            JournalEntry entry = entries.get(index);
            prompt.append("- Date: ").append(entry.getDate().toLocalDate())
                    .append(" | Title: ").append(safeValue(entry.getTitle()))
                    .append(" | Sentiment: ").append(entry.getSentiment() == null ? "Not selected" : entry.getSentiment())
                    .append(" | Reflection prompt: ").append(safeValue(entry.getReflectionPrompt()))
                    .append("\n");
        }
        return prompt.toString();
    }

    private Map<Sentiment, Integer> calculateSentimentBreakdown(List<JournalEntry> entries) {
        Map<Sentiment, Integer> counts = new EnumMap<>(Sentiment.class);
        for (JournalEntry entry : entries) {
            if (entry.getSentiment() != null) {
                Sentiment sentiment = entry.getSentiment();
                counts.put(sentiment, counts.getOrDefault(sentiment, 0) + 1);
            }
        }
        return counts;
    }

    private Sentiment calculateDominantSentiment(Map<Sentiment, Integer> sentimentBreakdown) {
        Sentiment dominant = null;
        int highestCount = 0;
        for (Sentiment sentiment : Sentiment.values()) {
            int count = sentimentBreakdown.getOrDefault(sentiment, 0);
            if (count > highestCount) {
                dominant = sentiment;
                highestCount = count;
            }
        }
        return dominant;
    }

    private Set<LocalDate> getUniqueJournalDays(List<JournalEntry> entries) {
        Set<LocalDate> journalDays = new HashSet<>();
        for (JournalEntry entry : entries) {
            journalDays.add(entry.getDate().toLocalDate());
        }
        return journalDays;
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "Not provided" : value.trim();
    }

    private double roundToTwoDecimalPlaces(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String callOpenAI(String prompt) {
        try {
            return openAIService.sendPrompt(aiConfigurationService.getSystemPrompt(), prompt);
        } catch (OpenAIServiceException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed", exception);
        }
    }

    private AIAnalyticsInsight parseInsight(String rawResponse, String username, LocalDate periodStart,
                                            LocalDate periodEnd, int sourceEntryCount) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode insightJson = objectMapper.readTree(content);

            AIAnalyticsInsight insight = new AIAnalyticsInsight();
            insight.setUsername(username);
            insight.setPeriodStart(periodStart);
            insight.setPeriodEnd(periodEnd);
            insight.setSourceEntryCount(sourceEntryCount);
            insight.setPatternInsight(getRequiredField(insightJson, "summary"));
            insight.setPositiveObservation(getRequiredField(insightJson, "positiveObservation"));
            insight.setReflectionQuestion(getRequiredField(insightJson, "reflectionQuestion"));
            insight.setEncouragement(getRequiredField(insightJson, "encouragement"));
            insight.setModelUsed(openAIService.getConfiguredModel());
            insight.setGeneratedAt(LocalDateTime.now());
            return insight;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Malformed response from OpenAI", exception);
        }
    }

    private String getRequiredField(JsonNode json, String fieldName) {
        String value = json.path(fieldName).asText();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing field: " + fieldName);
        }
        return value.trim();
    }

    private AIAnalyticsInsightResponseDTO toResponseDTO(AIAnalyticsInsight insight) {
        return AIAnalyticsInsightResponseDTO.builder()
                .periodStart(insight.getPeriodStart())
                .periodEnd(insight.getPeriodEnd())
                .patternInsight(insight.getPatternInsight())
                .positiveObservation(insight.getPositiveObservation())
                .reflectionQuestion(insight.getReflectionQuestion())
                .encouragement(insight.getEncouragement())
                .generatedAt(insight.getGeneratedAt())
                .build();
    }
}
