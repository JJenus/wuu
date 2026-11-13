package com.surf.tracker.notification.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.surf.tracker.main.TrackingEngineApplication;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.NotificationHub;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.PasswordService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP/session/security/persistence workflow; no provider delivery is simulated. */
@SpringBootTest(classes = TrackingEngineApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"userauth.jwt.secret=test-only-notification-read-secret-at-least-32-characters",
        "spring.datasource.url=jdbc:h2:mem:notification_read_http;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jms.listener.auto-startup=false", "userauth.password.bcrypt-strength=4"})
@DirtiesContext
class NotificationReadHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired OrganizationRepository organizations;
    @Autowired PasswordService passwords;
    @Autowired PlatformTransactionManager transactions;

    record LoginInput(String email, String password) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Session(@JsonProperty("access_token") String accessToken) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeliveryView(@JsonProperty("delivery_id") String deliveryId, String status) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NotificationPage(List<DeliveryView> content, @JsonProperty("total_elements") long totalElements) {}
    record Owner(long userId, long organizationId) {}

    @Test void notifications_realSession_readAndBulkReadFilterPersistAndRejectForeignRecipients() {
        // given: explicitly provision owned/foreign notification records, not provider delivery.
        Owner owner = new TransactionTemplate(transactions).execute(status -> {
            Organization org = organizations.save(Organization.create("Notification HTTP", "notification-http"));
            Role role = roles.save(Role.orgRole(org, "VIEWER", "Authenticated self-service"));
            User user = new User();
            user.setEmail("notification-http@example.test");
            user.setPasswordHash(passwords.hash("eight123"));
            user.assignRole(role);
            user = users.save(user);
            provision("http-a", user.getId().toString(), org.getId().toString());
            provision("http-b", user.getId().toString(), org.getId().toString());
            provision("http-foreign", user.getId().toString(), Long.toString(org.getId() + 10000));
            return new Owner(user.getId(), org.getId());
        });
        var login = http.postForEntity("/api/v1/auth/login", new LoginInput("notification-http@example.test", "eight123"), Session.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        var headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        var request = new HttpEntity<Void>(headers);
        String path = "/api/v1/notifications/user/" + owner.userId();
        assertThat(http.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // when / then: source-of-truth page counts change only after persisted read operations.
        var before = http.exchange(path + "?unreadOnly=true&size=1&sort=deliveryId,asc", HttpMethod.GET, request, NotificationPage.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(before.getBody().totalElements()).isEqualTo(2);
        assertThat(before.getBody().content()).extracting(DeliveryView::deliveryId).containsExactly("http-a");
        assertThat(http.exchange("/api/v1/notifications/http-a/read", HttpMethod.POST, request, Void.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        var unread = http.exchange(path + "?unreadOnly=true", HttpMethod.GET, request, NotificationPage.class).getBody();
        assertThat(unread.totalElements()).isEqualTo(1);
        assertThat(unread.content()).extracting(DeliveryView::deliveryId).containsExactly("http-b");
        assertThat(unread.content()).extracting(DeliveryView::status).containsExactly("DELIVERED");
        assertThat(http.exchange(path + "?unreadOnly=false", HttpMethod.GET, request, NotificationPage.class).getBody().totalElements())
            .isEqualTo(2);
        assertThat(http.exchange(path + "/read-all", HttpMethod.POST, request, Void.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange(path + "?unreadOnly=true", HttpMethod.GET, request, NotificationPage.class).getBody().content()).isEmpty();
        assertThat(http.exchange("/api/v1/notifications/user/" + (owner.userId() + 10000) + "?unreadOnly=true",
            HttpMethod.GET, request, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void provision(String id, String userId, String tenantId) {
        NotificationHub hub = new NotificationHub();
        hub.setNotificationId("hub-" + id);
        hub.setUserId(userId);
        hub.setTenantId(tenantId);
        hub.setTemplateId("http-template");
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(id);
        delivery.setChannel(NotificationChannel.EMAIL);
        delivery.setRecipient("private@example.test");
        delivery.setTemplateId("http-template");
        delivery.setStatus(DeliveryStatus.DELIVERED);
        hub.addDelivery(delivery);
        em.persist(hub);
    }
}
