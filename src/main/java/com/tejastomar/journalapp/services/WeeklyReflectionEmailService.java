package com.tejastomar.journalapp.services;

import com.tejastomar.journalapp.dto.WeeklyAnalyticsResponseDTO;
import com.tejastomar.journalapp.dto.WeeklyReflectionEmailDTO;
import com.tejastomar.journalapp.dto.AIAnalyticsInsightResponseDTO;
import com.tejastomar.journalapp.entity.AIInsight;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.repository.AIInsightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class WeeklyReflectionEmailService {

    private static final String SUBJECT = "DKD Weekly Reflection 🌿";
    private static final String DEFAULT_SUMMARY = "No AI reflections were generated this week, but your journal entries still create a meaningful record of your experiences.";
    private static final String DEFAULT_POSITIVE_OBSERVATION = "Every entry is a step toward greater self-awareness.";
    private static final String DEFAULT_REFLECTION_QUESTION = "What would you like to carry with you into next week?";
    private static final String DEFAULT_ENCOURAGEMENT = "Keep reflecting. Small steps every day build lifelong self-awareness.";

//    @Autowired
//    private UserService userService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AIInsightRepository aiInsightRepository;

    @Autowired
    private EmailService emailService;

    public void sendWeeklyReport(User user) {
        if (!isEligibleForWeeklyReport(user)) {
            return;
        }

        WeeklyReflectionEmailDTO report = buildReport(user);
        emailService.sendEmail(report.getEmail(), SUBJECT, buildEmailBody(report));
    }

    private boolean isEligibleForWeeklyReport(User user) {
        return user != null
                && Boolean.TRUE.equals(user.getSentimentAnalysis())
                && user.getEmail() != null
                && !user.getEmail().trim().isEmpty();
    }

    private WeeklyReflectionEmailDTO buildReport(User user) {
        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics(user.getUserName());
        List<AIInsight> weeklyInsights = getWeeklyInsights(user.getUserName(), analytics.getWeekStart(), analytics.getWeekEnd());
        AIInsight latestInsight = getLatestInsight(weeklyInsights);
        AIAnalyticsInsightResponseDTO analyticsInsight = analytics.getAiInsight();

        return WeeklyReflectionEmailDTO.builder()
                .username(user.getUserName())
                .email(user.getEmail())
                .currentStreak(analytics.getCurrentStreak())
                .entriesThisWeek(analytics.getTotalEntriesThisWeek())
                .journalConsistency(analytics.getJournalConsistency())
                .dominantSentiment(analytics.getDominantSentiment())
                .averageEntriesPerDay(analytics.getAverageEntriesPerDay())
                .weeklySummary(buildWeeklySummary(weeklyInsights, analyticsInsight))
                .positiveObservation(getInsightValue(latestInsight, AIInsight::getPositiveObservation, DEFAULT_POSITIVE_OBSERVATION))
                .reflectionQuestion(getInsightValue(latestInsight, AIInsight::getReflectionQuestion, DEFAULT_REFLECTION_QUESTION))
                .encouragement(getInsightValue(latestInsight, AIInsight::getEncouragement, DEFAULT_ENCOURAGEMENT))
                .build();
    }

    private List<AIInsight> getWeeklyInsights(String username, LocalDate weekStart, LocalDate weekEnd) {
        List<AIInsight> insights = aiInsightRepository.findByUsername(username);
        if (insights == null || insights.isEmpty()) {
            return Collections.emptyList();
        }

        List<AIInsight> weeklyInsights = new ArrayList<>();
        for (AIInsight insight : insights) {
            if (isWithinWeek(insight, weekStart, weekEnd)) {
                weeklyInsights.add(insight);
            }
        }
        weeklyInsights.sort(Comparator.comparing(AIInsight::getGeneratedAt));
        return weeklyInsights;
    }
    private boolean isWithinWeek(AIInsight insight, LocalDate weekStart, LocalDate weekEnd) {
        if (insight == null || insight.getGeneratedAt() == null) {
            return false;
        }

        LocalDate generatedDate = insight.getGeneratedAt().toLocalDate();
        return !generatedDate.isBefore(weekStart) && !generatedDate.isAfter(weekEnd);
    }

    private AIInsight getLatestInsight(List<AIInsight> insights) {
        return insights.stream()
                .max(Comparator.comparing(AIInsight::getGeneratedAt))
                .orElse(null);
    }

    private String buildWeeklySummary(List<AIInsight> insights, AIAnalyticsInsightResponseDTO analyticsInsight) {
        StringBuilder summary = new StringBuilder();

        if (analyticsInsight != null) {
            appendSummary(summary, analyticsInsight.getPatternInsight());
        }

        for (AIInsight insight : insights) {
            appendSummary(summary, insight.getSummary());
        }

        return summary.length() == 0 ? DEFAULT_SUMMARY : summary.toString();
    }

    private void appendSummary(StringBuilder summary, String insightSummary) {
        if (insightSummary == null || insightSummary.trim().isEmpty()) {
            return;
        }

        if (summary.length() > 0) {
            summary.append(" ");
        }
        summary.append(insightSummary.trim());
    }

    private String getInsightValue(AIInsight insight, InsightValueExtractor extractor, String defaultValue) {
        if (insight == null) {
            return defaultValue;
        }

        String value = extractor.getValue(insight);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private String buildEmailBody(WeeklyReflectionEmailDTO report) {
        return "Hello " + report.getUsername() + ",\n\n"
                + "Here is your DKD Weekly Reflection.\n\n"
                + "🔥 Current Streak: " + report.getCurrentStreak() + " days\n"
                + "📝 Entries this week: " + report.getEntriesThisWeek() + "\n"
                + "📊 Journal Consistency: " + report.getJournalConsistency() + "%\n"
                + "😊 Dominant Emotion: " + formatSentiment(report.getDominantSentiment()) + "\n"
                + "📈 Average Entries Per Day: " + report.getAverageEntriesPerDay() + "\n\n"
                + "💡 Weekly Reflection\n"
                + report.getWeeklySummary() + "\n\n"
                + "🌱 Positive Observation\n"
                + report.getPositiveObservation() + "\n\n"
                + "❓ Reflection Question\n"
                + report.getReflectionQuestion() + "\n\n"
                + report.getEncouragement() + "\n\n"
                + "— DKD (Dimaag Ka Darpan)";
    }

    private String formatSentiment(Sentiment sentiment){
        return sentiment == null ? "Not enough sentiment data this week" : sentiment.toString();
    }

    @FunctionalInterface
    private interface InsightValueExtractor {
        String getValue(AIInsight insight);
    }
}
