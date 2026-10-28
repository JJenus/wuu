package com.surf.tracker.userauth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.userauth.application.dto.AssignRolesRequest;
import com.surf.tracker.userauth.application.dto.CreateUserRequest;
import com.surf.tracker.userauth.application.dto.UpdateUserRequest;
import com.surf.tracker.userauth.application.dto.UserResponse;
import com.surf.tracker.userauth.application.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(10L);
        TenantContext.setCurrentUserId(1L);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(authService)).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void me_returnsCurrentUser() throws Exception {
        // given
        when(authService.getCurrentUser(1L)).thenReturn(userResponse());

        // when & then
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void list_returnsPagedUsers() throws Exception {
        // given
        Page<UserResponse> page = new PageImpl<>(List.of(userResponse()),
            PageRequest.of(0, 50), 1);
        when(authService.listUsersInOrgPaged(eq(10L), any())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1L))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.pageNumber").value(0));
    }

    @Test
    void list_noOrg_returnsEmptyPage() throws Exception {
        // given
        TenantContext.clear();
        TenantContext.setCurrentUserId(1L);

        // when & then
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));

        verify(authService, never()).listUsersInOrgPaged(any(), any());
    }

    @Test
    void getById_returnsUser() throws Exception {
        // given
        when(authService.getUserById(1L, 10L)).thenReturn(userResponse());

        // when & then
        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L));

        verify(authService).getUserById(1L, 10L);
    }

    @Test
    void create_returnsCreatedUser() throws Exception {
        // given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("bob@example.com");
        request.setPassword("test-password-123");
        request.setFirstName("Bob");
        request.setLastName("Brown");
        when(authService.createUser("bob@example.com", "test-password-123", "Bob", "Brown", 10L))
            .thenReturn(userResponse());

        String body = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void updateUser_returnsUpdatedUser() throws Exception {
        // given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Alice");
        request.setLastName("Smith");
        request.setEmail("alice@example.com");
        when(authService.updateUserProfile(1L, 10L, "Alice", "Smith", "alice@example.com"))
            .thenReturn(userResponse());

        String body = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(put("/api/v1/users/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Alice"));

        verify(authService).updateUserProfile(1L, 10L, "Alice", "Smith", "alice@example.com");
    }

    @Test
    void deleteUser_returnsNoContent() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/users/1"))
            .andExpect(status().isNoContent());

        verify(authService).deleteUser(1L, 10L);
    }

    @Test
    void assignRoles_returnsUpdatedUser() throws Exception {
        // given
        AssignRolesRequest request = new AssignRolesRequest();
        request.setRoleIds(List.of(5L));
        request.setOrganizationId(10L);
        when(authService.assignRoles(1L, List.of(5L), 10L)).thenReturn(userResponse());

        String body = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(put("/api/v1/users/1/roles")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L));
    }

    private UserResponse userResponse() {
        UserResponse r = new UserResponse();
        r.setId(1L);
        r.setEmail("alice@example.com");
        r.setFirstName("Alice");
        r.setLastName("Smith");
        r.setEnabled(true);
        r.setLocked(false);
        r.setOrganizationId(10L);
        r.setRoles(List.of("OPERATOR"));
        return r;
    }
}
