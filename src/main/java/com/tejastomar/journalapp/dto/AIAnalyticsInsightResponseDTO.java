package com.tejastomar.journalapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalyticsInsightResponseDTO {

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String patternInsight;
    private String positiveObservation;
    private String reflectionQuestion;
    private String encouragement;
    private LocalDateTime generatedAt;
}
