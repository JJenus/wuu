package com.surf.tracker.alerting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.AlertRuleService;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
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
class AlertRuleControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
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
        mockMvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleKey").value("test-rule"))
                .andExpect(jsonPath("$.ruleName").value("Test Rule"));
    }

    @Test
    void createRule_invalidRequest_returnsBadRequest() throws Exception {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        // Missing required fields

        // when & then
        mockMvc.perform(post("/api/alert-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
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
        mockMvc.perform(get("/api/alert-rules")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ruleKey").value("test-rule"))
                .andExpect(jsonPath("$.pageNumber").value(0));
    }

    @Test
    void getRuleByKey_existingRule_returnsOk() throws Exception {
        // given
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey("test-rule");
        response.setRuleName("Test Rule");

        when(alertRuleService.getRuleByKey("test-rule")).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/alert-rules/test-rule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleKey").value("test-rule"));
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
        mockMvc.perform(put("/api/alert-rules/test-rule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleName").value("Updated Name"));
    }

    @Test
    void deleteRule_existingRule_returnsNoContent() throws Exception {
        // given
        doNothing().when(alertRuleService).deleteRule("test-rule");

        // when & then
        mockMvc.perform(delete("/api/alert-rules/test-rule"))
                .andExpect(status().isNoContent());
    }

    @Test
    void enableRule_existingRule_returnsOk() throws Exception {
        // given
        doNothing().when(alertRuleService).enableRule("test-rule");

        // when & then
        mockMvc.perform(patch("/api/alert-rules/test-rule/enable"))
                .andExpect(status().isOk());
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
        mockMvc.perform(post("/api/alert-rules/templates/overspeed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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
        mockMvc.perform(post("/api/alert-rules/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].ruleKey").value("rule-1"));
    }

    @Test
    void batchEnableRules_validRequest_returnsOk() throws Exception {
        // given
        List<String> ruleKeys = List.of("rule-1", "rule-2");
        doNothing().when(alertRuleService).batchEnableRules(anySet());

        // when & then
        mockMvc.perform(post("/api/alert-rules/batch/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ruleKeys)))
                .andExpect(status().isOk());
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
        mockMvc.perform(get("/api/alert-rules/enabled")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].enabled").value(true));
    }
}
