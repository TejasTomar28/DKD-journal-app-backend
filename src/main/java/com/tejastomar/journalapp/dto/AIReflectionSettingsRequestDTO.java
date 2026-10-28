package com.tejastomar.journalapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIReflectionSettingsRequestDTO {

    @NotNull
    private Boolean enabled;
}
