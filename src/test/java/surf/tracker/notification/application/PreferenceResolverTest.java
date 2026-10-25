package com.surf.tracker.notification.application;

import com.surf.tracker.notification.domain.entity.NotificationPreference;
import com.surf.tracker.notification.domain.entity.NotificationTemplate;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenceResolverTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private PreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PreferenceResolver(preferenceRepository);
    }

    @Test
    void resolvePreferences_noPreferenceDefault_defaultsAllEnabled() {
        when(preferenceRepository.findByUserIdAndCategory("user_1", "marketing"))
            .thenReturn(Optional.empty());

        var prefs = resolver.resolvePreferences("user_1", "marketing", null);

        for (NotificationChannel ch : NotificationChannel.values()) {
            assertThat(prefs.get(ch).enabled()).isTrue();
        }
    }

    @Test
    void resolvePreferences_mandatoryTemplate_bypassesPreferences() {
        NotificationTemplate mandatory = new NotificationTemplate();
        mandatory.setMandatory(true);

        var prefs = resolver.resolvePreferences("user_1", "security", mandatory);

        for (NotificationChannel ch : NotificationChannel.values()) {
            assertThat(prefs.get(ch).enabled()).isTrue();
            assertThat(prefs.get(ch).mandatory()).isTrue();
        }
    }

    @Test
    void resolvePreferences_mandatoryTemplate_skipsDatabaseLookup() {
        NotificationTemplate mandatory = new NotificationTemplate();
        mandatory.setMandatory(true);

        var prefs = resolver.resolvePreferences("user_1", "security", mandatory);

        for (NotificationChannel ch : NotificationChannel.values()) {
            assertThat(prefs.get(ch).mandatory()).isTrue();
        }
    }

    @Test
    void resolvePreferences_preferenceDisabledAll_disablesAllChannels() {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId("user_1");
        pref.setCategory("marketing");
        pref.setEnabled(false);

        when(preferenceRepository.findByUserIdAndCategory("user_1", "marketing"))
            .thenReturn(Optional.of(pref));

        var prefs = resolver.resolvePreferences("user_1", "marketing", null);

        for (NotificationChannel ch : NotificationChannel.values()) {
            assertThat(prefs.get(ch).enabled()).isFalse();
        }
    }

    @Test
    void resolvePreferences_preferenceEnablesSubset_onlySubsetEnabled() {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId("user_1");
        pref.setCategory("marketing");
        pref.setEnabled(true);
        pref.setEnabledChannels(java.util.Set.of(
            NotificationChannel.EMAIL, NotificationChannel.WEBSOCKET
        ));

        when(preferenceRepository.findByUserIdAndCategory("user_1", "marketing"))
            .thenReturn(Optional.of(pref));

        var prefs = resolver.resolvePreferences("user_1", "marketing", null);

        assertThat(prefs.get(NotificationChannel.EMAIL).enabled()).isTrue();
        assertThat(prefs.get(NotificationChannel.WEBSOCKET).enabled()).isTrue();
        assertThat(prefs.get(NotificationChannel.SMS).enabled()).isFalse();
        assertThat(prefs.get(NotificationChannel.MOBILE_PUSH).enabled()).isFalse();
    }

    @Test
    void isChannelEnabled_enabledPreference_returnsTrue() {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId("user_1");
        pref.setCategory("orders");
        pref.setEnabled(true);
        pref.setEnabledChannels(java.util.Set.of(NotificationChannel.EMAIL));

        when(preferenceRepository.findByUserIdAndCategory("user_1", "orders"))
            .thenReturn(Optional.of(pref));

        boolean enabled = resolver.isChannelEnabled("user_1", "orders",
            NotificationChannel.EMAIL, null);

        assertThat(enabled).isTrue();
    }
}