package com.tejastomar.journalapp.dto;

import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.enums.Sentiment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponseDTO {

    private String id;
    private String title;
    private String content;
    private Sentiment sentiment;
    private List<String> tags;
    private boolean aiEnabled;
    private String reflectionPrompt;
    private LocalDateTime date;

    public static JournalEntryResponseDTO from(JournalEntry entry) {
        return JournalEntryResponseDTO.builder()
                .id(entry.getId() == null ? null : entry.getId().toHexString())
                .title(entry.getTitle())
                .content(entry.getContent())
                .sentiment(entry.getSentiment())
                .tags(entry.getTags())
                .aiEnabled(entry.isAiEnabled())
                .reflectionPrompt(entry.getReflectionPrompt())
                .date(entry.getDate())
                .build();
    }
}
