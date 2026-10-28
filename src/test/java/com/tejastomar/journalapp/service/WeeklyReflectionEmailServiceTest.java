package com.tejastomar.journalapp.service;

import com.tejastomar.journalapp.dto.WeeklyAnalyticsResponseDTO;
import com.tejastomar.journalapp.dto.AIAnalyticsInsightResponseDTO;
import com.tejastomar.journalapp.entity.AIInsight;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.repository.AIInsightRepository;
import com.tejastomar.journalapp.services.AnalyticsService;
import com.tejastomar.journalapp.services.EmailService;
import com.tejastomar.journalapp.services.WeeklyReflectionEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyReflectionEmailServiceTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private AIInsightRepository aiInsightRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private WeeklyReflectionEmailService weeklyReflectionEmailService;

    @Test
    void sendWeeklyReport_skipsUserWhoHasNotOptedIn() {

        User user = createUser(false);

        weeklyReflectionEmailService.sendWeeklyReport(user);

        verify(analyticsService, never()).getWeeklyAnalytics(anyString());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void sendWeeklyReport_sendsWeeklyReflectionEmail() {

        User user = createUser(true);

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        WeeklyAnalyticsResponseDTO analytics =
                WeeklyAnalyticsResponseDTO.builder()
                        .username("tejas")
                        .weekStart(weekStart)
                        .weekEnd(today)
                        .currentStreak(3)
                        .totalEntriesThisWeek(4)
                        .journalConsistency(66.67)
                        .dominantSentiment(Sentiment.HAPPY)
                        .averageEntriesPerDay(1.33)
                        .build();

        AIInsight olderInsight = createInsight(
                "I noticed steady progress.",
                "An earlier observation.",
                "Earlier question?",
                "Earlier encouragement.",
                LocalDateTime.now().minusHours(2)
        );

        AIInsight latestInsight = createInsight(
                "I made time to reflect.",
                "You showed up for yourself.",
                "What small habit will you continue next week?",
                "Keep building on this momentum.",
                LocalDateTime.now().minusHours(1)
        );

        AIInsight previousWeekInsight = createInsight(
                "Should never appear",
                "Old",
                "Old?",
                "Old",
                weekStart.minusDays(1).atStartOfDay()
        );

        when(analyticsService.getWeeklyAnalytics("tejas"))
                .thenReturn(analytics);

        when(aiInsightRepository.findByUsername("tejas"))
                .thenReturn(List.of(
                        olderInsight,
                        latestInsight,
                        previousWeekInsight
                ));

        weeklyReflectionEmailService.sendWeeklyReport(user);

        ArgumentCaptor<String> bodyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService).sendEmail(
                eq("tejas@example.com"),
                eq("DKD Weekly Reflection 🌿"),
                bodyCaptor.capture()
        );

        String emailBody = bodyCaptor.getValue();

        assertTrue(emailBody.contains("Current Streak: 3 days"));
        assertTrue(emailBody.contains("Entries this week: 4"));
        assertTrue(emailBody.contains("Journal Consistency: 66.67%"));
        assertTrue(emailBody.contains("I noticed steady progress."));
        assertTrue(emailBody.contains("I made time to reflect."));
        assertTrue(emailBody.contains("You showed up for yourself."));
        assertTrue(emailBody.contains("What small habit will you continue next week?"));
        assertTrue(emailBody.contains("Keep building on this momentum."));
        assertFalse(emailBody.contains("Should never appear"));
    }

    @Test
    void sendWeeklyReport_usesFallbackValuesWhenNoInsightsExist() {

        User user = createUser(true);

        LocalDate today = LocalDate.now();

        WeeklyAnalyticsResponseDTO analytics =
                WeeklyAnalyticsResponseDTO.builder()
                        .username("tejas")
                        .weekStart(today.with(DayOfWeek.MONDAY))
                        .weekEnd(today)
                        .build();

        when(analyticsService.getWeeklyAnalytics("tejas"))
                .thenReturn(analytics);

        when(aiInsightRepository.findByUsername("tejas"))
                .thenReturn(List.of());

        weeklyReflectionEmailService.sendWeeklyReport(user);

        ArgumentCaptor<String> bodyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService).sendEmail(
                anyString(),
                anyString(),
                bodyCaptor.capture()
        );

        String emailBody = bodyCaptor.getValue();

        assertTrue(emailBody.contains(
                "No AI reflections were generated this week"));

        assertTrue(emailBody.contains(
                "Not enough sentiment data this week"));

        assertTrue(emailBody.contains(
                "Every entry is a step toward greater self-awareness."));

        assertTrue(emailBody.contains(
                "What would you like to carry with you into next week?"));

        assertTrue(emailBody.contains(
                "Keep reflecting. Small steps every day build lifelong self-awareness."));
    }

    @Test
    void sendWeeklyReport_includesStoredGlobalAIAnalyticsInsightWithoutCallingOpenAI() {
        User user = createUser(true);
        LocalDate today = LocalDate.now();
        AIAnalyticsInsightResponseDTO globalInsight = AIAnalyticsInsightResponseDTO.builder()
                .patternInsight("Recent entries appear to show placement-related pressure.")
                .build();
        WeeklyAnalyticsResponseDTO analytics = WeeklyAnalyticsResponseDTO.builder()
                .username("tejas")
                .weekStart(today.with(DayOfWeek.MONDAY))
                .weekEnd(today)
                .aiInsight(globalInsight)
                .build();
        when(analyticsService.getWeeklyAnalytics("tejas")).thenReturn(analytics);
        when(aiInsightRepository.findByUsername("tejas")).thenReturn(List.of());

        weeklyReflectionEmailService.sendWeeklyReport(user);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(anyString(), anyString(), bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("Recent entries appear to show placement-related pressure."));
    }

    private User createUser(boolean optedIn) {

        User user = new User();
        user.setUserName("tejas");
        user.setEmail("tejas@example.com");
        user.setSentimentAnalysis(optedIn);

        return user;
    }

    private AIInsight createInsight(
            String summary,
            String observation,
            String question,
            String encouragement,
            LocalDateTime generatedAt
    ) {

        AIInsight insight = new AIInsight();

        insight.setSummary(summary);
        insight.setPositiveObservation(observation);
        insight.setReflectionQuestion(question);
        insight.setEncouragement(encouragement);
        insight.setGeneratedAt(generatedAt);

        return insight;
    }
}
