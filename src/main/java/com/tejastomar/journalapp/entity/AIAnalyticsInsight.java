package com.tejastomar.journalapp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A stored, user-level reflection over recent journal metadata. It is separate
 * from AIInsight, which belongs to one individual journal entry.
 */
@Document(collection = "ai_analytics_insights")
@Data
@NoArgsConstructor
public class AIAnalyticsInsight {

    @Id
    private ObjectId id;
    private String username;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int sourceEntryCount;
    private String patternInsight;
    private String positiveObservation;
    private String reflectionQuestion;
    private String encouragement;
    private String modelUsed;
    private LocalDateTime generatedAt;
}
