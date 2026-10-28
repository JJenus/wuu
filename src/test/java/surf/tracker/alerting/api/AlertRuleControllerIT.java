package com.surf.tracker.alerting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.AlertRuleService;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import com.surf.tracker.userauth.infrastructure.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertRuleController.class)
@ContextConfiguration(classes = AlertRuleControllerIT.Config.class)
@WithMockUser(authorities = {"alerts.read", "alerts.write"})
class AlertRuleControllerIT {

    @Configuration(proxyBeanMethods = false)
    @Import({AlertRuleController.class, SecurityConfig.class, JwtAuthFilter.class, AuthRateLimitFilter.class})
    static class Config {}

    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthIdentityService identities;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AlertRuleService alertRuleService;

    @Test
    void createRule_validRequest_returnsCreated() throws Exception {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("test-rule");
        request.setRuleName("Test Rule");
        request.setRuleType("SPEED");
        request.setParameters("{\"speedLimit\":80}");
        request.setPriority(1);
        request.setEnabled(true);

        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("test-rule");
        response.setRuleName("Test Rule");
        response.setRuleType(AlertRuleType.SPEED);

        when(alertRuleService.createRule(any(CreateAlertRuleRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/alerts/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rule_key").value("test-rule"))
                .andExpect(jsonPath("$.rule_name").value("Test Rule"))
                .andExpect(jsonPath("$.rule_type").value("SPEED"));
    }

    @Test
    void createRule_invalidRequest_returnsBadRequest() throws Exception {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        // Missing required fields

        // when & then
        mockMvc.perform(post("/alerts/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(alertRuleService);
    }

    @Test
    @WithMockUser(authorities = "alerts.read")
    void getAllRules_withPagination_returnsOk() throws Exception {
        // given
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("test-rule");
        response.setRuleName("Test Rule");
        response.setRuleType(AlertRuleType.SPEED);

        Page<AlertRuleResponse> page = new PageImpl<>(List.of(response));
        PagedResponse<AlertRuleResponse> pagedResponse = new PagedResponse<>(page);

        when(alertRuleService.getAllRulesPaged(any(SearchRequest.class))).thenReturn(pagedResponse);

        // when & then
        mockMvc.perform(get("/alerts/rules")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rule_key").value("test-rule"))
                .andExpect(jsonPath("$.page_number").value(0));
        verify(alertRuleService).getAllRulesPaged(argThat(search -> search.getPage() == 0
            && search.getSize() == 20 && search.getSortBy().equals("createdAt")
            && search.getSortDirection() == Sort.Direction.DESC));
    }

    @Test
    @WithMockUser(authorities = "alerts.read")
    void getRuleByKey_existingRule_returnsOk() throws Exception {
        // given
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("test-rule");
        response.setRuleName("Test Rule");

        when(alertRuleService.getRuleByKey("test-rule")).thenReturn(response);

        // when & then
        mockMvc.perform(get("/alerts/rules/test-rule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule_key").value("test-rule"));
    }

    @Test
    void updateRule_validRequest_returnsOk() throws Exception {
        // given
        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setRuleName("Updated Name");

        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("test-rule");
        response.setRuleName("Updated Name");

        when(alertRuleService.updateRule(eq("test-rule"), any(UpdateAlertRuleRequest.class)))
            .thenReturn(response);

        // when & then
        mockMvc.perform(put("/alerts/rules/test-rule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule_name").value("Updated Name"));
    }

    @Test
    void deleteRule_existingRule_returnsNoContent() throws Exception {
        // given
        doNothing().when(alertRuleService).deleteRule("test-rule");

        // when & then
        mockMvc.perform(delete("/alerts/rules/test-rule"))
                .andExpect(status().isNoContent());
        verify(alertRuleService).deleteRule("test-rule");
    }

    @Test
    void enableRule_existingRule_returnsOk() throws Exception {
        // given
        doNothing().when(alertRuleService).enableRule("test-rule");

        // when & then
        mockMvc.perform(patch("/alerts/rules/test-rule/enable"))
                .andExpect(status().isOk());
        verify(alertRuleService).enableRule("test-rule");
    }

    @Test
    void createOverspeedRule_validRequest_returnsCreated() throws Exception {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setRuleKey("overspeed-rule");
        request.setRuleName("Overspeed Alert");
        request.setSpeedLimit(80.0f);
        request.setVehicleIds(Set.of("vehicle-001"));

        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("overspeed-rule");

        when(alertRuleService.createOverspeedRule(any(OverspeedRuleTemplateRequest.class)))
            .thenReturn(response);

        // when & then
        mockMvc.perform(post("/alerts/rules/templates/overspeed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rule_key").value("overspeed-rule"));
    }

    @Test
    void batchCreateRules_validRequest_returnsCreated() throws Exception {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("rule-1");
        request.setRuleName("Rule 1");
        request.setRuleType("SPEED");
        request.setParameters("{}");

        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("rule-1");

        when(alertRuleService.batchCreateRules(anyList())).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(post("/alerts/rules/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].rule_key").value("rule-1"));
    }

    @Test
    void batchEnableRules_validRequest_returnsOk() throws Exception {
        // given
        List<String> ruleKeys = List.of("rule-1", "rule-2");
        doNothing().when(alertRuleService).batchEnableRules(anySet());

        // when & then
        mockMvc.perform(post("/alerts/rules/batch/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ruleKeys)))
                .andExpect(status().isOk());
        verify(alertRuleService).batchEnableRules(Set.of("rule-1", "rule-2"));
    }

    @Test
    void getEnabledRules_returnsFilteredResults() throws Exception {
        // given
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("enabled-rule");
        response.setEnabled(true);

        Page<AlertRuleResponse> page = new PageImpl<>(List.of(response));
        PagedResponse<AlertRuleResponse> pagedResponse = new PagedResponse<>(page);

        when(alertRuleService.getEnabledRulesPaged(any(SearchRequest.class))).thenReturn(pagedResponse);

        // when & then
        mockMvc.perform(get("/alerts/rules/enabled")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].enabled").value(true));
        verify(alertRuleService).getEnabledRulesPaged(argThat(search -> Boolean.TRUE.equals(search.getEnabled())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
    @WithAnonymousUser
    void request_anonymous_returnsUnauthorized(String method) throws Exception {
        // when & then
        mockMvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/alerts/rules/test-rule")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(alertRuleService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    @WithMockUser(authorities = "alerts.read")
    void mutation_readerOnly_returnsForbidden(String method) throws Exception {
        // given
        String path = switch (method) {
            case "POST" -> "/alerts/rules";
            case "PATCH" -> "/alerts/rules/test-rule/enable";
            default -> "/alerts/rules/test-rule";
        };
        // when & then
        mockMvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(alertRuleService);
    }

    @Test
    @WithMockUser(authorities = "alerts.write")
    void getRule_writerWithoutRead_returnsForbidden() throws Exception {
        // when & then
        mockMvc.perform(get("/alerts/rules/test-rule"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(alertRuleService);
    }
}
