package com.tejastomar.journalapp.dto;

import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.enums.Sentiment;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalEntryResponseDTOTests {

    @Test
    void from_mapsObjectIdToHexStringAndPreservesJournalFields() {
        ObjectId id = new ObjectId();
        JournalEntry entry = new JournalEntry();
        entry.setId(id);
        entry.setTitle("A pause");
        entry.setContent("Some words.");
        entry.setSentiment(Sentiment.HAPPY);
        entry.setTags(List.of("growth"));
        entry.setAiEnabled(false);
        entry.setReflectionPrompt("Which emotion stayed with you most strongly today?");
        entry.setDate(LocalDateTime.of(2026, 10, 21, 10, 30));

        JournalEntryResponseDTO response = JournalEntryResponseDTO.from(entry);

        assertEquals(id.toHexString(), response.getId());
        assertTrue(ObjectId.isValid(response.getId()));
        assertEquals(entry.getTitle(), response.getTitle());
        assertEquals(entry.getContent(), response.getContent());
        assertEquals(entry.getSentiment(), response.getSentiment());
        assertEquals(entry.getTags(), response.getTags());
        assertEquals(entry.isAiEnabled(), response.isAiEnabled());
        assertEquals(entry.getReflectionPrompt(), response.getReflectionPrompt());
        assertEquals(entry.getDate(), response.getDate());
    }
}
