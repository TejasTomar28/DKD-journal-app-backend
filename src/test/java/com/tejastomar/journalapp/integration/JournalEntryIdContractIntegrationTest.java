package com.tejastomar.journalapp.integration;

import com.tejastomar.journalapp.dto.JournalEntryResponseDTO;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.repository.JournalEntryRepository;
import com.tejastomar.journalapp.repository.UserRepository;
import com.tejastomar.journalapp.services.UserService;
import com.tejastomar.journalapp.utils.JwtUtil;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.listener.auto-startup=false")
class JournalEntryIdContractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String username;
    private String token;
    private ObjectId createdJournalId;

    @BeforeEach
    void setUp() {
        username = "journal-contract-" + UUID.randomUUID();
        User user = new User();
        user.setUserName(username);
        user.setEmail(username + "@example.com");
        user.setPassword("test-password");
        userService.saveNewUser(user);
        token = jwtUtil.generateToken(username);
    }

    @AfterEach
    void cleanUp() {
        if (createdJournalId != null) {
            journalEntryRepository.deleteById(createdJournalId);
        }
        userRepository.deleteByUserName(username);
    }

    @Test
    void createThenGetJournal_usesTheSameHexObjectId() {
        ResponseEntity<JournalEntryResponseDTO> createResponse = restTemplate.exchange(
                endpoint("/journal"),
                HttpMethod.POST,
                new HttpEntity<>(journalRequest(), authenticatedHeaders()),
                JournalEntryResponseDTO.class
        );

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        JournalEntryResponseDTO createdJournal = createResponse.getBody();
        assertNotNull(createdJournal);
        assertTrue(ObjectId.isValid(createdJournal.getId()));
        assertEquals("A pause", createdJournal.getTitle());
        createdJournalId = new ObjectId(createdJournal.getId());

        ResponseEntity<JournalEntryResponseDTO> getResponse = restTemplate.exchange(
                endpoint("/journal/id/" + createdJournal.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authenticatedHeaders()),
                JournalEntryResponseDTO.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        assertEquals(createdJournal.getId(), getResponse.getBody().getId());
        assertEquals(createdJournal.getTitle(), getResponse.getBody().getTitle());
    }

    private Map<String, Object> journalRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("title", "A pause");
        request.put("content", "Some words.");
        request.put("sentiment", "HAPPY");
        request.put("tags", new String[]{"growth"});
        request.put("aiEnabled", false);
        request.put("reflectionPrompt", "Which emotion stayed with you most strongly today?");
        return request;
    }

    private HttpHeaders authenticatedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String endpoint(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
