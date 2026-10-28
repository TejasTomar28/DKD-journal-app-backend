package com.tejastomar.journalapp.controller;

import com.tejastomar.journalapp.dto.JournalEntryResponseDTO;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.services.JournalEntryService;
import com.tejastomar.journalapp.services.JournalSearchService;
import com.tejastomar.journalapp.services.UserService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class JournalEntryControllerTests {

    private final JournalEntryService journalEntryService = Mockito.mock(JournalEntryService.class);
    private final UserService userService = Mockito.mock(UserService.class);
    private JournalEntryController journalEntryController;

    @BeforeEach
    void setUp() {
        journalEntryController = new JournalEntryController();
        ReflectionTestUtils.setField(journalEntryController, "journalEntryService", journalEntryService);
        ReflectionTestUtils.setField(journalEntryController, "userService", userService);
        ReflectionTestUtils.setField(journalEntryController, "journalSearchService", Mockito.mock(JournalSearchService.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tejas", null, List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createEntry_returnsPersistedJournalWithUsableHexId() {
        JournalEntry savedEntry = journalEntry();
        when(journalEntryService.saveEntry(any(JournalEntry.class), eq("tejas"))).thenReturn(savedEntry);

        ResponseEntity<JournalEntryResponseDTO> response = journalEntryController.createEntry(new JournalEntry());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(savedEntry.getId().toHexString(), response.getBody().getId());
        assertTrue(ObjectId.isValid(response.getBody().getId()));
    }

    @Test
    void getJournalEntryById_acceptsReturnedHexIdAndReturnsSameJournal() {
        JournalEntry savedEntry = journalEntry();
        User user = new User();
        user.setJournalEntries(List.of(savedEntry));
        when(userService.findByUserName("tejas")).thenReturn(user);
        when(journalEntryService.findById(savedEntry.getId())).thenReturn(Optional.of(savedEntry));

        ResponseEntity<JournalEntryResponseDTO> response =
                journalEntryController.getJournalEntryById(savedEntry.getId().toHexString());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(savedEntry.getId().toHexString(), response.getBody().getId());
    }

    @Test
    void getAllJournalEntries_returnsOnlyHexStringIds() {
        JournalEntry firstEntry = journalEntry();
        JournalEntry secondEntry = journalEntry();
        User user = new User();
        user.setJournalEntries(List.of(firstEntry, secondEntry));
        when(userService.findByUserName("tejas")).thenReturn(user);

        ResponseEntity<List<JournalEntryResponseDTO>> response =
                journalEntryController.getAllJournalEntriesOfUser();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(firstEntry.getId().toHexString(), response.getBody().get(0).getId());
        assertEquals(secondEntry.getId().toHexString(), response.getBody().get(1).getId());
        assertTrue(response.getBody().stream().allMatch(entry -> ObjectId.isValid(entry.getId())));
    }

    private JournalEntry journalEntry() {
        JournalEntry entry = new JournalEntry();
        entry.setId(new ObjectId());
        entry.setTitle("A pause");
        entry.setContent("Some words.");
        entry.setSentiment(Sentiment.HAPPY);
        entry.setTags(List.of("growth"));
        entry.setAiEnabled(false);
        entry.setReflectionPrompt("Which emotion stayed with you most strongly today?");
        return entry;
    }
}
