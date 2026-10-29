package com.surf.tracker.subscription.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.surf.tracker.subscription.domain.SubscriptionTestBuilder.*;

class AdminGrantTest {
    @Test
    void create_explicit_trial_preserves_admin_configuration() {
        // given / when
        var grant = grant(3);
        // then
        assertThat(grant.kind()).isEqualTo(AdminGrant.Kind.TRIAL);
        assertThat(grant.units()).isEqualTo(3);
        assertThat(grant.administratorId()).isEqualTo("configured-admin");
    }

    @Test
    void create_invalid_grant_rejects() {
        // given / when / then
        assertThatThrownBy(() -> grant(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminGrant(AdminGrant.Kind.TRIAL, " ", "reason", START, END, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminGrant(AdminGrant.Kind.TRIAL, "admin", " ", START, END, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminGrant(AdminGrant.Kind.TRIAL, "admin", "reason", END, START, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
