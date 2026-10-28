package com.tejastomar.journalapp.controller;

import com.tejastomar.journalapp.api.response.WeatherResponse;
import com.tejastomar.journalapp.dto.AIReflectionSettingsRequestDTO;
import com.tejastomar.journalapp.dto.AIReflectionSettingsResponseDTO;
import com.tejastomar.journalapp.dto.UserGreetingResponseDTO;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.repository.UserRepository;
import com.tejastomar.journalapp.services.UserService;
import com.tejastomar.journalapp.services.WeatherService;
import com.tejastomar.journalapp.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/user")//puri class pe mapping kar dega
@Tag(name = "User APIs", description = "Read, Update & Delete User")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository UserRepository;

    @Autowired
    private WeatherService weatherService;


    @Operation(
            summary = "Update User Profile",
            description = "Updates username and password of the authenticated user."
    )
    @PutMapping
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        String username = SecurityUtil.getCurrentUsername();
        User userInDb = userService.findByUserName(username);
        userInDb.setUserName(user.getUserName());
        userInDb.setPassword(user.getPassword());
        userService.saveNewUser(userInDb);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(
            summary = "Get AI Reflection Setting",
            description = "Returns whether AI reflection features are enabled for the authenticated user."
    )
    @GetMapping("/ai-reflection")
    public ResponseEntity<AIReflectionSettingsResponseDTO> getAIReflectionSetting() {
        User user = userService.findByUserName(SecurityUtil.getCurrentUsername());
        return ResponseEntity.ok(AIReflectionSettingsResponseDTO.builder()
                .enabled(Boolean.TRUE.equals(user.getAiReflectionEnabled()))
                .build());
    }

    @Operation(
            summary = "Update AI Reflection Setting",
            description = "Enables or disables user-level AI pattern reflections. Individual journal content still requires per-journal AI consent."
    )
    @PutMapping("/ai-reflection")
    public ResponseEntity<Void> updateAIReflectionSetting(
            @Valid @RequestBody AIReflectionSettingsRequestDTO request) {
        User user = userService.findByUserName(SecurityUtil.getCurrentUsername());
        user.setAiReflectionEnabled(request.getEnabled());
        userService.saveUser(user);
        return ResponseEntity.noContent().build();
    }


    @Operation(
            summary = "Delete User Account",
            description = "Deletes the currently authenticated user account."
    )
    @DeleteMapping
    public ResponseEntity<?> deleteUserById(){
        UserRepository.deleteByUserName(SecurityUtil.getCurrentUsername());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }


    @Operation(
            summary = "Get Personalized Greeting",
            description = "Returns dashboard-friendly greeting and current weather information."
    )
    @GetMapping
    public ResponseEntity<UserGreetingResponseDTO> greeting(){
        String username = SecurityUtil.getCurrentUsername();
        WeatherResponse weatherResponse= weatherService.getWeather("Delhi");
        return ResponseEntity.ok(UserGreetingResponseDTO.builder()
                .username(username)
                .message("Welcome back, " + username + "!")
                .weather(toWeatherDTO(weatherResponse))
                .build());
    }

    private UserGreetingResponseDTO.WeatherDTO toWeatherDTO(WeatherResponse weatherResponse) {
        if (weatherResponse == null || weatherResponse.getCurrent() == null) {
            return null;
        }

        return UserGreetingResponseDTO.WeatherDTO.builder()
                .city("Delhi")
                .temperature(weatherResponse.getCurrent().getTemperature())
                .feelsLike(weatherResponse.getCurrent().getFeelsLike())
                .description(getWeatherDescription(weatherResponse))
                .build();
    }

    private String getWeatherDescription(WeatherResponse weatherResponse) {
        if (weatherResponse.getCurrent().getWeatherDescriptions() == null
                || weatherResponse.getCurrent().getWeatherDescriptions().isEmpty()) {
            return null;
        }
        return weatherResponse.getCurrent().getWeatherDescriptions().get(0);
    }
}
