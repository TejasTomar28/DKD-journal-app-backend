package com.tejastomar.journalapp.repository;

import com.tejastomar.journalapp.entity.AIAnalyticsInsight;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;

public interface AIAnalyticsInsightRepository extends MongoRepository<AIAnalyticsInsight, ObjectId> {

    AIAnalyticsInsight findFirstByUsernameOrderByGeneratedAtDesc(String username);

    AIAnalyticsInsight findFirstByUsernameAndPeriodStartAndPeriodEndAndSourceEntryCount(
            String username, LocalDate periodStart, LocalDate periodEnd, int sourceEntryCount);
}
