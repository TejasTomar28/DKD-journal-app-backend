package com.tejastomar.journalapp.services;

import com.tejastomar.journalapp.dto.WeeklyAnalyticsResponseDTO;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyticsService {

    @Autowired
    private UserService userService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AIAnalyticsInsightService aiAnalyticsInsightService;

    public WeeklyAnalyticsResponseDTO getWeeklyAnalytics(String username) {
        User user = getUser(username);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today;
        int elapsedDays = calculateElapsedDays(weekStart, weekEnd);
        List<JournalEntry> allEntries = getJournalEntries(user);
        List<JournalEntry> weeklyEntries = getEntriesForWeek(allEntries, weekStart, weekEnd);
        Map<Sentiment, Integer> sentimentBreakdown = calculateSentimentBreakdown(weeklyEntries);
        Set<LocalDate> journalDays = getUniqueJournalDays(weeklyEntries);

        return WeeklyAnalyticsResponseDTO.builder()
                .username(user.getUserName())
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .totalEntriesThisWeek(weeklyEntries.size())
                .currentStreak(dashboardService.calculateCurrentStreak(allEntries))
                .dominantSentiment(calculateDominantSentiment(sentimentBreakdown))
                .sentimentBreakdown(sentimentBreakdown)
                .averageEntriesPerDay(calculateAverageEntriesPerDay(weeklyEntries.size(), elapsedDays))
                .journalConsistency(calculateJournalConsistency(journalDays.size(), elapsedDays))
                .aiInsight(aiAnalyticsInsightService.getLatestInsightIfEnabled(user))
                .build();
    }

    private User getUser(String username) {
        User user = userService.findByUserName(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        return user;
    }

    private List<JournalEntry> getJournalEntries(User user) {
        return user.getJournalEntries() == null ? Collections.emptyList() : user.getJournalEntries();
    }

    private List<JournalEntry> getEntriesForWeek(List<JournalEntry> entries, LocalDate weekStart, LocalDate weekEnd) {
        List<JournalEntry> weeklyEntries = new ArrayList<>();

        for (JournalEntry entry : entries) {
            if (isWithinWeek(entry, weekStart, weekEnd)) {
                weeklyEntries.add(entry);
            }
        }

        return weeklyEntries;
    }

    private boolean isWithinWeek(JournalEntry entry, LocalDate weekStart, LocalDate weekEnd) {
        if (entry == null || entry.getDate() == null) {
            return false;
        }

        LocalDate entryDate = entry.getDate().toLocalDate();
        return !entryDate.isBefore(weekStart) && !entryDate.isAfter(weekEnd);
    }

    private Map<Sentiment, Integer> calculateSentimentBreakdown(List<JournalEntry> entries) {
        Map<Sentiment, Integer> sentimentBreakdown = new EnumMap<>(Sentiment.class);

        for (JournalEntry entry : entries) {
            if (entry.getSentiment() != null) {
                Sentiment sentiment = entry.getSentiment();
                sentimentBreakdown.put(sentiment, sentimentBreakdown.getOrDefault(sentiment, 0) + 1);
            }
        }

        return sentimentBreakdown;
    }

    private Sentiment calculateDominantSentiment(Map<Sentiment, Integer> sentimentBreakdown) {
        Sentiment dominantSentiment = null;
        int highestCount = 0;

        for (Sentiment sentiment : Sentiment.values()) {
            int count = sentimentBreakdown.getOrDefault(sentiment, 0);
            if (count > highestCount) {
                highestCount = count;
                dominantSentiment = sentiment;
            }
        }

        return dominantSentiment;
    }

    private int calculateElapsedDays(LocalDate weekStart, LocalDate today) {
        return (int) ChronoUnit.DAYS.between(weekStart, today) + 1;
    }

    private Set<LocalDate> getUniqueJournalDays(List<JournalEntry> entries) {
        Set<LocalDate> journalDays = new HashSet<>();

        for (JournalEntry entry : entries) {
            if (entry != null && entry.getDate() != null) {
                journalDays.add(entry.getDate().toLocalDate());
            }
        }

        return journalDays;
    }

    private double calculateAverageEntriesPerDay(int totalEntries, int elapsedDays) {
        return roundToTwoDecimalPlaces(totalEntries / (double) elapsedDays);
    }

    private double calculateJournalConsistency(int journalDays, int elapsedDays) {
        return roundToTwoDecimalPlaces((journalDays / (double) elapsedDays) * 100);
    }

    private double roundToTwoDecimalPlaces(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
