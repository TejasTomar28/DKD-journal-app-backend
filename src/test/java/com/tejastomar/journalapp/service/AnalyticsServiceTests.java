package com.tejastomar.journalapp.service;

import com.tejastomar.journalapp.dto.WeeklyAnalyticsResponseDTO;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.services.AnalyticsService;
import com.tejastomar.journalapp.services.AIAnalyticsInsightService;
import com.tejastomar.journalapp.services.DashboardService;
import com.tejastomar.journalapp.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private AIAnalyticsInsightService aiAnalyticsInsightService;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getWeeklyAnalytics_returnsEmptyAnalyticsWhenNoEntriesAreInTheWeek() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<JournalEntry> entries = List.of(entry(Sentiment.HAPPY, monday.minusDays(1)));
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", entries));

        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics("tejas");

        assertEquals(monday, analytics.getWeekStart());
        assertEquals(today, analytics.getWeekEnd());
        assertEquals(0, analytics.getTotalEntriesThisWeek());
        assertNull(analytics.getDominantSentiment());
        assertEquals(Map.of(), analytics.getSentimentBreakdown());
        assertEquals(0.0, analytics.getAverageEntriesPerDay());
        assertEquals(0.0, analytics.getJournalConsistency());
    }

    @Test
    void getWeeklyAnalytics_returnsBreakdownDominantSentimentAndAverage() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        int elapsedDays = elapsedDays(monday, today);
        List<JournalEntry> entries = List.of(
                entry(Sentiment.HAPPY, monday),
                entry(Sentiment.HAPPY, today),
                entry(Sentiment.SAD, today),
                entry(Sentiment.ANXIOUS, today)
        );
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", entries));
        when(dashboardService.calculateCurrentStreak(entries)).thenReturn(4);

        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics("tejas");

        assertEquals(4, analytics.getTotalEntriesThisWeek());
        assertEquals(Sentiment.HAPPY, analytics.getDominantSentiment());
        assertEquals(2, analytics.getSentimentBreakdown().get(Sentiment.HAPPY));
        assertEquals(1, analytics.getSentimentBreakdown().get(Sentiment.SAD));
        assertEquals(1, analytics.getSentimentBreakdown().get(Sentiment.ANXIOUS));
        assertEquals(roundToTwoDecimalPlaces(4 / (double) elapsedDays), analytics.getAverageEntriesPerDay());
        assertEquals(4, analytics.getCurrentStreak());
        verify(dashboardService).calculateCurrentStreak(entries);
    }

    @Test
    void getWeeklyAnalytics_includesMondayAndTodayButExcludesOutsideDates() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<JournalEntry> entries = List.of(
                entry(Sentiment.HAPPY, monday.minusDays(1)),
                entry(Sentiment.HAPPY, monday),
                entry(Sentiment.SAD, today),
                entry(Sentiment.ANXIOUS, today.plusDays(1))
        );
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", entries));

        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics("tejas");

        assertEquals(2, analytics.getTotalEntriesThisWeek());
        assertEquals(1, analytics.getSentimentBreakdown().get(Sentiment.HAPPY));
        assertEquals(1, analytics.getSentimentBreakdown().get(Sentiment.SAD));
    }

    @Test
    void getWeeklyAnalytics_calculatesAverageUsingElapsedDays() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<JournalEntry> entries = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            entries.add(entry(Sentiment.HAPPY, monday));
        }
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", entries));

        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics("tejas");

        assertEquals(6, analytics.getTotalEntriesThisWeek());
        assertEquals(roundToTwoDecimalPlaces(6 / (double) elapsedDays(monday, today)), analytics.getAverageEntriesPerDay());
    }

    @Test
    void getWeeklyAnalytics_calculatesConsistencyUsingUniqueJournalDays() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<JournalEntry> entries = List.of(
                entry(Sentiment.HAPPY, monday),
                entry(Sentiment.SAD, today),
                entry(Sentiment.ANXIOUS, today)
        );
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", entries));

        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics("tejas");

        Set<LocalDate> uniqueDays = new HashSet<>();
        uniqueDays.add(monday);
        uniqueDays.add(today);
        double expectedConsistency = roundToTwoDecimalPlaces(
                (uniqueDays.size() / (double) elapsedDays(monday, today)) * 100
        );

        assertEquals(expectedConsistency, analytics.getJournalConsistency());
    }

    private User user(String username, List<JournalEntry> entries) {
        User user = new User();
        user.setUserName(username);
        user.setJournalEntries(entries);
        return user;
    }

    private JournalEntry entry(Sentiment sentiment, LocalDate date) {
        JournalEntry entry = new JournalEntry();
        entry.setSentiment(sentiment);
        entry.setDate(LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), 12, 0));
        return entry;
    }

    private int elapsedDays(LocalDate monday, LocalDate today) {
        return (int) (today.toEpochDay() - monday.toEpochDay()) + 1;
    }

    private double roundToTwoDecimalPlaces(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
