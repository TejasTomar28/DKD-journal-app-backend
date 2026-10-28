package com.tejastomar.journalapp.entity;

import com.tejastomar.journalapp.enums.Sentiment;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

//this is our POJO class.

@Data
@NoArgsConstructor
@Document(collection = "journal_entries")
public class JournalEntry {

    @Id
    private ObjectId id;
    @NonNull
    private String title;
    private String content;
    private LocalDateTime date;
    private Sentiment sentiment;

    private boolean aiEnabled;
    private List<String> tags;
    private String reflectionPrompt;

}
