package com.tejastomar.journalapp.controller;

import com.tejastomar.journalapp.dto.WeeklyAnalyticsResponseDTO;
import com.tejastomar.journalapp.dto.AIAnalyticsInsightResponseDTO;
import com.tejastomar.journalapp.services.AIAnalyticsInsightService;
import com.tejastomar.journalapp.services.AnalyticsService;
import com.tejastomar.journalapp.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analytics APIs")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AIAnalyticsInsightService aiAnalyticsInsightService;

    @GetMapping("/weekly")
    @Operation(
            summary = "Get Weekly Analytics",
            description = "Returns weekly journal insights for the authenticated user from Monday through Sunday."
    )
    public ResponseEntity<WeeklyAnalyticsResponseDTO> getWeeklyAnalytics() {
        WeeklyAnalyticsResponseDTO analytics = analyticsService.getWeeklyAnalytics(
                SecurityUtil.getCurrentUsername()
        );
        return ResponseEntity.ok(analytics);
    }

    @PostMapping("/ai-insight")
    @Operation(
            summary = "Generate Recent AI Pattern Insight",
            description = "Generates or reuses an AI reflection for the authenticated user's last 30 days of journal metadata. Requires the global AI reflection setting."
    )
    public ResponseEntity<AIAnalyticsInsightResponseDTO> generateRecentAIInsight() {
        return ResponseEntity.ok(aiAnalyticsInsightService.generateRecentInsight(
                SecurityUtil.getCurrentUsername()
        ));
    }
}
