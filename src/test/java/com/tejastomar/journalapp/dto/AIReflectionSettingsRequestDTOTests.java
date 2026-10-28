package com.tejastomar.journalapp.dto;

import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIReflectionSettingsRequestDTOTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validation_rejectsMissingEnabledValue() {
        AIReflectionSettingsRequestDTO request = new AIReflectionSettingsRequestDTO(null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void validation_acceptsExplicitEnabledValue() {
        AIReflectionSettingsRequestDTO request = new AIReflectionSettingsRequestDTO(true);

        assertTrue(validator.validate(request).isEmpty());
    }
}
