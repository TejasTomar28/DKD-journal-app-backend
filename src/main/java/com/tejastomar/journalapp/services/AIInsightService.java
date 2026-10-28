package com.tejastomar.journalapp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tejastomar.journalapp.dto.AIInsightResponseDTO;
import com.tejastomar.journalapp.entity.AIInsight;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.repository.AIInsightRepository;
import com.tejastomar.journalapp.repository.JournalEntryRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AIInsightService {

    @Autowired
    private UserService userService;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private AIInsightRepository aiInsightRepository;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private AIConfigurationService aiConfigurationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIInsightResponseDTO generateInsight(String username, ObjectId journalId) {
        User user = getUser(username);
        verifyAIReflectionEnabled(user);
        JournalEntry journalEntry = getOwnedJournalEntry(user, journalId);
        verifyAIEnabled(journalEntry);

        AIInsight existingInsight = aiInsightRepository.findByJournalEntryId(journalId);
        if (existingInsight != null) {
            return toResponseDTO(existingInsight);
        }

        // TODO: Publish JournalAIRequestedEvent here when generation moves to Kafka asynchronously.
        AIInsight insight = generateAndParseInsight(username, journalEntry);
        journalEntry.setSentiment(insight.getDetectedSentiment());
        journalEntryRepository.save(journalEntry);
        return toResponseDTO(aiInsightRepository.save(insight));
    }

    public AIInsightResponseDTO getStoredInsight(String username, ObjectId journalId) {
        User user = getUser(username);
        verifyAIReflectionEnabled(user);
        getOwnedJournalEntry(user, journalId);
        AIInsight insight = aiInsightRepository.findByJournalEntryId(journalId);
        if (insight == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI insight not found");
        }
        return toResponseDTO(insight);
    }

    private User getUser(String username) {
        User user = userService.findByUserName(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Journal entry is not owned by the current user");
        }
        return user;
    }

    private JournalEntry getOwnedJournalEntry(User user, ObjectId journalId) {
        JournalEntry journalEntry = journalEntryRepository.findById(journalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found"));

        if (!ownsJournalEntry(user.getJournalEntries(), journalId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Journal entry is not owned by the current user");
        }
        return journalEntry;
    }

    private boolean ownsJournalEntry(List<JournalEntry> journalEntries, ObjectId journalId) {
        if (journalEntries == null) {
            return false;
        }

        for (JournalEntry entry : journalEntries) {
            if (entry != null && journalId.equals(entry.getId())) {
                return true;
            }
        }
        return false;
    }

    private void verifyAIEnabled(JournalEntry journalEntry) {
        if (!journalEntry.isAiEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI insight generation is disabled for this journal entry");
        }
    }

    private void verifyAIReflectionEnabled(User user) {
        if (!Boolean.TRUE.equals(user.getAiReflectionEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AI reflection is disabled in account settings");
        }
    }

    private AIInsight generateAndParseInsight(String username, JournalEntry journalEntry) {
        System.out.println("STEP 1 : Building Prompt");
        String prompt = buildPrompt(journalEntry);
        System.out.println("STEP 2 : Calling OpenAI");
        try {
            String rawResponse = openAIService.sendPrompt(aiConfigurationService.getSystemPrompt(), prompt);
            System.out.println("STEP 3 : Response received");
            System.out.println(rawResponse);
            return parseInsight(rawResponse, journalEntry.getId(), username);
        } catch (OpenAIServiceException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI request failed", exception);
        }
    }

    private String buildPrompt(JournalEntry journalEntry) {
        if (journalEntry.getContent() == null || journalEntry.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal content is required for AI insight generation");
        }

        StringBuilder prompt = new StringBuilder("Journal Context:\n");
        appendField(prompt, "Title", journalEntry.getTitle());
        appendField(prompt, "Current Sentiment", journalEntry.getSentiment() == null
                ? null : journalEntry.getSentiment().name());
        appendTags(prompt, journalEntry.getTags());
        appendField(prompt, "Reflection Prompt", journalEntry.getReflectionPrompt());
        prompt.append("Journal Content:\n").append(journalEntry.getContent());
        return prompt.toString();
    }

    private void appendField(StringBuilder prompt, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            prompt.append(label).append(": ").append(value.trim()).append("\n");
        }
    }

    private void appendTags(StringBuilder prompt, List<String> tags) {
        if (tags != null && !tags.isEmpty()) {
            prompt.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }
    }

    private AIInsight parseInsight(String rawResponse, ObjectId journalEntryId, String username) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode insightJson = objectMapper.readTree(content);

            AIInsight insight = new AIInsight();

            insight.setJournalEntryId(journalEntryId);
            insight.setSummary(getRequiredField(insightJson, "summary"));
            insight.setDetectedSentiment(parseSentiment(getRequiredField(insightJson, "detectedSentiment")));
            insight.setPositiveObservation(getRequiredField(insightJson, "positiveObservation"));
            insight.setReflectionQuestion(getRequiredField(insightJson, "reflectionQuestion"));
            insight.setEncouragement(getRequiredField(insightJson, "encouragement"));
            insight.setModelUsed(openAIService.getConfiguredModel());
            insight.setGeneratedAt(LocalDateTime.now());
            insight.setGenerated(true);
            insight.setUsername(username);

            return insight;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Malformed response from OpenAI", exception);
        }
    }

    private String getRequiredField(JsonNode json, String fieldName) {
        String value = json.path(fieldName).asText();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing field: " + fieldName);
        }
        return value;
    }

    private Sentiment parseSentiment(String detectedSentiment) {
        try {
            return Sentiment.valueOf(detectedSentiment.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenAI returned an invalid detected sentiment", exception);
        }
    }

    private AIInsightResponseDTO toResponseDTO(AIInsight insight) {
        return AIInsightResponseDTO.builder()
                .summary(insight.getSummary())
                .detectedSentiment(insight.getDetectedSentiment())
                .positiveObservation(insight.getPositiveObservation())
                .reflectionQuestion(insight.getReflectionQuestion())
                .encouragement(insight.getEncouragement())
                .generatedAt(insight.getGeneratedAt())
                .build();
    }
}
