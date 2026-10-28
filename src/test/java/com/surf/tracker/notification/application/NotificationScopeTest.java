package com.surf.tracker.notification.application;

import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.notification.infrastructure.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationScopeTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void findNotifications_foreignRecipient_rejectsBeforeQuery() {
        // given
        TenantContext.setCurrentUserId(1L);
        TenantContext.setCurrentOrgId(10L);
        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        var service = new NotificationQueryService(deliveries,
            mock(NotificationPreferenceRepository.class), mock(NotificationTemplateRepository.class));
        // when / then
        assertThatThrownBy(() -> service.findNotifications("2", null, null, null, PageRequest.of(0, 20)))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(deliveries);
    }

    @Test void getPreferences_missingIdentity_rejectsBeforeQuery() {
        // given
        var preferences = mock(NotificationPreferenceRepository.class);
        var service = new NotificationQueryService(mock(DeliveryRepository.class), preferences,
            mock(NotificationTemplateRepository.class));
        // when / then
        assertThatThrownBy(() -> service.getUserPreferences("1")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(preferences);
    }
}
