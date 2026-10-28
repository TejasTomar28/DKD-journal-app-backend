package com.tejastomar.journalapp.controller;

import com.tejastomar.journalapp.dto.JournalEntryResponseDTO;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.services.JournalEntryService;
import com.tejastomar.journalapp.services.JournalSearchService;
import com.tejastomar.journalapp.services.UserService;
import com.tejastomar.journalapp.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tejastomar.journalapp.enums.Sentiment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/journal")//puri class pe mapping kar dega
@Tag(name="Journal APIs")
public class JournalEntryController {

    @Autowired
    private JournalEntryService journalEntryService;

    @Autowired
    private UserService userService;

    @Autowired
    private JournalSearchService journalSearchService;

    @Operation(
            summary = "Get All Journal Entries",
            description = "Fetches all journal entries belonging to the authenticated user."
    )
    @GetMapping
    public ResponseEntity<List<JournalEntryResponseDTO>> getAllJournalEntriesOfUser(){
        String username = SecurityUtil.getCurrentUsername();
        User user=userService.findByUserName(username);
        List<JournalEntry> all = user.getJournalEntries();
        if(all!=null && !all.isEmpty()){
            return ResponseEntity.ok(toResponseDTOs(all));
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }


    @Operation(
            summary = "Search and Filter Journal Entries",
            description = "Searches the authenticated user's journal titles and optionally filters by sentiment and inclusive date range. Sort defaults to desc."
    )
    @GetMapping("/search")
    public ResponseEntity<List<JournalEntryResponseDTO>> searchJournalEntries(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Sentiment sentiment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "desc") String sort) {
        try {
            List<JournalEntry> entries = journalSearchService.searchJournalEntries(
                    SecurityUtil.getCurrentUsername(), title, sentiment, startDate, endDate, sort
            );
            return ResponseEntity.ok(toResponseDTOs(entries));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }


    @Operation(
            summary = "Create Journal Entry",
            description = "Creates a new journal entry for the authenticated user."
    )
    @PostMapping
    public ResponseEntity<JournalEntryResponseDTO> createEntry(@RequestBody JournalEntry myEntry){ //requestbody ka kaam hai data(from body) ko object mei convert karna
       try{
           String username = SecurityUtil.getCurrentUsername();
           JournalEntry savedEntry = journalEntryService.saveEntry(myEntry, username);
           return new ResponseEntity<>(JournalEntryResponseDTO.from(savedEntry), HttpStatus.CREATED);
       }catch(Exception e){
           e.printStackTrace();
           return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
       }
    }


    @Operation(
            summary = "Get Journal Entry By ID",
            description = "Retrieves a specific journal entry using its unique identifier."
    )
    @GetMapping("id/{myId}")
    public ResponseEntity<JournalEntryResponseDTO> getJournalEntryById(@PathVariable String myId){
        ObjectId id = new ObjectId(myId);
        String username = SecurityUtil.getCurrentUsername();
        User user=userService.findByUserName(username);
        List<JournalEntry> collect=user.getJournalEntries().stream().filter(entry->entry.getId().equals(id)).collect(Collectors.toList());
        if(!collect.isEmpty()) {
            Optional<JournalEntry> journalEntry = journalEntryService.findById(id);
            if(journalEntry.isPresent()){
                return ResponseEntity.ok(JournalEntryResponseDTO.from(journalEntry.get()));
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }


    @Operation(
            summary = "Delete Journal Entry",
            description = "Deletes a journal entry belonging to the authenticated user."
    )
    @DeleteMapping("id/{myId}")
    public ResponseEntity<? > deleteJournalEntryById(@PathVariable ObjectId myId){
        String username = SecurityUtil.getCurrentUsername();
        boolean removed=journalEntryService.deleteById(myId,username);
        if(removed){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }else{
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


    @Operation(
            summary = "Update Journal Entry",
            description = "Updates title and content of an existing journal entry."
    )
    @PutMapping("id/{myId}")
    public ResponseEntity<JournalEntryResponseDTO> updateJournalById(@PathVariable ObjectId myId, @RequestBody JournalEntry newEntry) {
        String username = SecurityUtil.getCurrentUsername();
        User user=userService.findByUserName(username);
        List<JournalEntry> collect=user.getJournalEntries().stream().filter(entry->entry.getId().equals(myId)).collect(Collectors.toList());
        if(!collect.isEmpty()) {
            Optional<JournalEntry> journalEntry = journalEntryService.findById(myId);
            if(journalEntry.isPresent()){
                JournalEntry old=journalEntry.get();
                old.setTitle(newEntry.getTitle()!=null && !newEntry.getTitle().equals("") ? newEntry.getTitle(): old.getTitle());
                old.setContent(newEntry.getContent()!=null && !newEntry.getContent().equals("") ? newEntry.getContent() : old.getContent());
                if (newEntry.getSentiment() != null) {
                    old.setSentiment(newEntry.getSentiment());
                }

                if (newEntry.getTags() != null) {
                    old.setTags(newEntry.getTags());
                }

                if (newEntry.getReflectionPrompt() != null) {
                    old.setReflectionPrompt(newEntry.getReflectionPrompt());
                }

                old.setAiEnabled(newEntry.isAiEnabled());
                JournalEntry savedEntry = journalEntryService.saveEntry(old);
                return ResponseEntity.ok(JournalEntryResponseDTO.from(savedEntry));
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    private List<JournalEntryResponseDTO> toResponseDTOs(List<JournalEntry> entries) {
        return entries.stream()
                .map(JournalEntryResponseDTO::from)
                .collect(Collectors.toList());
    }

}
