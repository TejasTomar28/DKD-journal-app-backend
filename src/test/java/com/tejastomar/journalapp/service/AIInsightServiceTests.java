package com.tejastomar.journalapp.service;

import com.tejastomar.journalapp.dto.AIInsightResponseDTO;
import com.tejastomar.journalapp.entity.AIInsight;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.enums.Sentiment;
import com.tejastomar.journalapp.repository.AIInsightRepository;
import com.tejastomar.journalapp.repository.JournalEntryRepository;
import com.tejastomar.journalapp.services.AIInsightService;
import com.tejastomar.journalapp.services.AIConfigurationService;
import com.tejastomar.journalapp.services.OpenAIService;
import com.tejastomar.journalapp.services.UserService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIInsightServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private AIInsightRepository aiInsightRepository;

    @Mock
    private OpenAIService openAIService;

    @Mock
    private AIConfigurationService aiConfigurationService;

    @InjectMocks
    private AIInsightService aiInsightService;

    @Test
    void generateInsight_rejectsJournalEntriesWithAIDisabled() {
        JournalEntry journalEntry = journalEntry(false);
        mockOwnedJournal("tejas", journalEntry);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> aiInsightService.generateInsight("tejas", journalEntry.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(openAIService, never()).sendPrompt(any(), any());
    }

    @Test
    void generateInsight_rejectsRequestsWhenGlobalAIReflectionIsDisabled() {
        JournalEntry journalEntry = journalEntry(true);
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", List.of(journalEntry), false));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> aiInsightService.generateInsight("tejas", journalEntry.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(journalEntryRepository, never()).findById(any());
        verify(openAIService, never()).sendPrompt(any(), any());
    }

    @Test
    void generateInsight_returnsStoredInsightWithoutCallingOpenAI() {
        JournalEntry journalEntry = journalEntry(true);
        AIInsight storedInsight = insight(journalEntry.getId());
        mockOwnedJournal("tejas", journalEntry);
        when(aiInsightRepository.findByJournalEntryId(journalEntry.getId())).thenReturn(storedInsight);

        AIInsightResponseDTO response = aiInsightService.generateInsight("tejas", journalEntry.getId());

        assertEquals("Stored summary", response.getSummary());
        verify(openAIService, never()).sendPrompt(any(), any());
    }

    @Test
    void generateInsight_parsesAndSavesNewInsight() {
        JournalEntry journalEntry = journalEntry(true);
        mockOwnedJournal("tejas", journalEntry);
        when(aiInsightRepository.findByJournalEntryId(journalEntry.getId())).thenReturn(null);
        when(aiConfigurationService.getSystemPrompt()).thenReturn("Configured system prompt");
        when(openAIService.sendPrompt(eq("Configured system prompt"), any())).thenReturn(openAIResponse());
        when(openAIService.getConfiguredModel()).thenReturn("configured-model");
        when(aiInsightRepository.save(any(AIInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AIInsightResponseDTO response = aiInsightService.generateInsight("tejas", journalEntry.getId());

        assertEquals("A concise reflection.", response.getSummary());
        assertEquals(Sentiment.HAPPY, response.getDetectedSentiment());
        verify(aiInsightRepository).save(any(AIInsight.class));
        verify(journalEntryRepository).save(journalEntry);
        assertEquals(Sentiment.HAPPY, journalEntry.getSentiment());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAIService).sendPrompt(eq("Configured system prompt"), promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("Title: A peaceful day"));
        assertTrue(prompt.contains("Current Sentiment: HAPPY"));
        assertTrue(prompt.contains("Tags: growth, work"));
        assertTrue(prompt.contains("Reflection Prompt: What helped you feel grounded today?"));
        assertTrue(prompt.contains("Journal Content:\nI had a productive and peaceful day."));
    }

    @Test
    void generateInsight_rejectsJournalEntriesNotOwnedByCurrentUser() {
        JournalEntry journalEntry = journalEntry(true);
        when(journalEntryRepository.findById(journalEntry.getId())).thenReturn(Optional.of(journalEntry));
        when(userService.findByUserName("tejas")).thenReturn(user("tejas", List.of(), true));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> aiInsightService.generateInsight("tejas", journalEntry.getId()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(openAIService, never()).sendPrompt(any(), any());
    }

    @Test
    void generateInsight_rejectsMalformedOpenAIResponses() {
        JournalEntry journalEntry = journalEntry(true);
        mockOwnedJournal("tejas", journalEntry);
        when(aiInsightRepository.findByJournalEntryId(journalEntry.getId())).thenReturn(null);
        when(aiConfigurationService.getSystemPrompt()).thenReturn("Configured system prompt");
        when(openAIService.sendPrompt(any(), any())).thenReturn("{\"choices\":[]}");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> aiInsightService.generateInsight("tejas", journalEntry.getId()));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    private void mockOwnedJournal(String username, JournalEntry journalEntry) {
        when(journalEntryRepository.findById(journalEntry.getId())).thenReturn(Optional.of(journalEntry));
        when(userService.findByUserName(username)).thenReturn(user(username, List.of(journalEntry), true));
    }

    private JournalEntry journalEntry(boolean aiEnabled) {
        JournalEntry entry = new JournalEntry();
        entry.setId(new ObjectId());
        entry.setTitle("A peaceful day");
        entry.setContent("I had a productive and peaceful day.");
        entry.setAiEnabled(aiEnabled);
        entry.setSentiment(Sentiment.HAPPY);
        entry.setTags(List.of("growth", "work"));
        entry.setReflectionPrompt("What helped you feel grounded today?");
        return entry;
    }

    private User user(String username, List<JournalEntry> entries, boolean aiReflectionEnabled) {
        User user = new User();
        user.setUserName(username);
        user.setJournalEntries(entries);
        user.setAiReflectionEnabled(aiReflectionEnabled);
        return user;
    }

    private AIInsight insight(ObjectId journalEntryId) {
        AIInsight insight = new AIInsight();
        insight.setJournalEntryId(journalEntryId);
        insight.setSummary("Stored summary");
        insight.setDetectedSentiment(Sentiment.HAPPY);
        insight.setPositiveObservation("You made time for yourself.");
        insight.setReflectionQuestion("What would you repeat tomorrow?");
        insight.setEncouragement("Keep noticing small wins.");
        return insight;
    }

    private String openAIResponse() {
        return "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"A concise reflection.\\\",\\\"detectedSentiment\\\":\\\" happy \\\",\\\"positiveObservation\\\":\\\"You made time for yourself.\\\",\\\"reflectionQuestion\\\":\\\"What would you repeat tomorrow?\\\",\\\"encouragement\\\":\\\"Keep noticing small wins.\\\"}\"}}]}";
    }
}
