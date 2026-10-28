package com.tejastomar.journalapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGreetingResponseDTO {

    private String username;
    private String message;
    private WeatherDTO weather;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherDTO {
        private String city;
        private Integer temperature;
        private Integer feelsLike;
        private String description;
    }
}
