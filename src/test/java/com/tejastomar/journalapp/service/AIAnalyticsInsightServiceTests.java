package com.tejastomar.journalapp.service;

import com.tejastomar.journalapp.dto.AIAnalyticsInsightResponseDTO;
import com.tejastomar.journalapp.entity.AIAnalyticsInsight;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.repository.AIAnalyticsInsightRepository;
import com.tejastomar.journalapp.services.AIAnalyticsInsightService;
import com.tejastomar.journalapp.services.AIConfigurationService;
import com.tejastomar.journalapp.services.OpenAIService;
import com.tejastomar.journalapp.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIAnalyticsInsightServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private AIAnalyticsInsightRepository aiAnalyticsInsightRepository;

    @Mock
    private OpenAIService openAIService;

    @Mock
    private AIConfigurationService aiConfigurationService;

    @InjectMocks
    private AIAnalyticsInsightService aiAnalyticsInsightService;

    @Test
    void generateRecentInsight_rejectsRequestsWhenGlobalAIIsDisabled() {
        when(userService.findByUserName("tejas"))
                .thenReturn(user(false, List.of(entry("Placement preparation", Sentiment.ANXIOUS, LocalDate.now()))));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> aiAnalyticsInsightService.generateRecentInsight("tejas"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(openAIService, never()).sendPrompt(anyString(), anyString());
    }

    @Test
    void generateRecentInsight_reusesCurrentInsightWhenTheSourceEntryCountIsUnchanged() {
        LocalDate today = LocalDate.now();
        User user = user(true, List.of(entry("Placement preparation", Sentiment.ANXIOUS, today)));
        AIAnalyticsInsight existing = storedInsight(today.minusDays(29), today);
        when(userService.findByUserName("tejas")).thenReturn(user);
        when(aiAnalyticsInsightRepository.findFirstByUsernameAndPeriodStartAndPeriodEndAndSourceEntryCount(
                "tejas", today.minusDays(29), today, 1)).thenReturn(existing);

        AIAnalyticsInsightResponseDTO response = aiAnalyticsInsightService.generateRecentInsight("tejas");

        assertEquals("Recent entries suggest placement preparation has been weighing on you.",
                response.getPatternInsight());
        verify(openAIService, never()).sendPrompt(anyString(), anyString());
    }

    @Test
    void generateRecentInsight_sendsMetadataButNeverJournalContent() {
        LocalDate today = LocalDate.now();
        JournalEntry entry = entry("Placement preparation", Sentiment.ANXIOUS, today);
        entry.setContent("This private content must never be part of aggregate analysis.");
        entry.setReflectionPrompt("What would help you feel prepared?");
        when(userService.findByUserName("tejas")).thenReturn(user(true, List.of(entry)));
        when(aiAnalyticsInsightRepository.findFirstByUsernameAndPeriodStartAndPeriodEndAndSourceEntryCount(
                anyString(), any(), any(), anyInt())).thenReturn(null);
        when(aiConfigurationService.getSystemPrompt()).thenReturn("Configured system prompt");
        when(openAIService.sendPrompt(eq("Configured system prompt"), anyString())).thenReturn(openAIResponse());
        when(openAIService.getConfiguredModel()).thenReturn("configured-model");
        when(aiAnalyticsInsightRepository.save(any(AIAnalyticsInsight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AIAnalyticsInsightResponseDTO response = aiAnalyticsInsightService.generateRecentInsight("tejas");

        assertEquals("Recent entries appear to show placement-related pressure.", response.getPatternInsight());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAIService).sendPrompt(eq("Configured system prompt"), promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Title: Placement preparation"));
        assertTrue(prompt.contains("Sentiment: ANXIOUS"));
        assertTrue(prompt.contains("Reflection prompt: What would help you feel prepared?"));
        assertTrue(!prompt.contains("This private content must never be part of aggregate analysis."));
    }

    @Test
    void getLatestInsightIfEnabled_returnsNothingWhenAIIsDisabled() {
        assertNull(aiAnalyticsInsightService.getLatestInsightIfEnabled(user(false, List.of())));
        verify(aiAnalyticsInsightRepository, never()).findFirstByUsernameOrderByGeneratedAtDesc(anyString());
    }

    private User user(boolean aiReflectionEnabled, List<JournalEntry> entries) {
        User user = new User();
        user.setUserName("tejas");
        user.setAiReflectionEnabled(aiReflectionEnabled);
        user.setJournalEntries(entries);
        return user;
    }

    private JournalEntry entry(String title, Sentiment sentiment, LocalDate date) {
        JournalEntry entry = new JournalEntry();
        entry.setTitle(title);
        entry.setSentiment(sentiment);
        entry.setDate(date.atTime(12, 0));
        return entry;
    }

    private AIAnalyticsInsight storedInsight(LocalDate periodStart, LocalDate periodEnd) {
        AIAnalyticsInsight insight = new AIAnalyticsInsight();
        insight.setPeriodStart(periodStart);
        insight.setPeriodEnd(periodEnd);
        insight.setPatternInsight("Recent entries suggest placement preparation has been weighing on you.");
        insight.setPositiveObservation("You kept showing up for yourself.");
        insight.setReflectionQuestion("What support would ease the pressure?");
        insight.setEncouragement("Keep taking one step at a time.");
        insight.setGeneratedAt(LocalDateTime.now());
        return insight;
    }

    private String openAIResponse() {
        return "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"Recent entries appear to show placement-related pressure.\\\",\\\"detectedSentiment\\\":\\\"ANXIOUS\\\",\\\"positiveObservation\\\":\\\"You stayed consistent with reflection.\\\",\\\"reflectionQuestion\\\":\\\"What support could make preparation feel lighter?\\\",\\\"encouragement\\\":\\\"Small steps still count.\\\"}\"}}]}";
    }
}
