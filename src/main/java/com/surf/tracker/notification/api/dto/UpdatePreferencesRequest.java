package com.surf.tracker.notification.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class UpdatePreferencesRequest {

    @NotEmpty(message = "Preferences list cannot be empty")
    @Valid
    private List<PreferenceUpdateDto> preferences;

    public List<PreferenceUpdateDto> getPreferences() { return preferences; }
    public void setPreferences(List<PreferenceUpdateDto> preferences) { this.preferences = preferences; }

    public static class PreferenceUpdateDto {
        @NotEmpty(message = "Category is required")
        private String category;

        private boolean enabled = true;

        @NotEmpty(message = "At least one channel must be specified")
        private List<String> channels;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getChannels() { return channels; }
        public void setChannels(List<String> channels) { this.channels = channels; }
    }
}