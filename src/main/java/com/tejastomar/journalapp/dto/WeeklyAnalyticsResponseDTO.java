package com.tejastomar.journalapp.dto;

import com.tejastomar.journalapp.enums.Sentiment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyAnalyticsResponseDTO {
    private String username;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int totalEntriesThisWeek;
    private int currentStreak;
    private Sentiment dominantSentiment;
    private Map<Sentiment, Integer> sentimentBreakdown;
    private double averageEntriesPerDay;
    private double journalConsistency;
    private AIAnalyticsInsightResponseDTO aiInsight;
}
