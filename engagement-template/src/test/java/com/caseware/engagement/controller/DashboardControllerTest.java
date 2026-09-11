package com.caseware.engagement.controller;

import com.caseware.engagement.dto.EngagementReadModel;
import com.caseware.engagement.dto.RecordDecisionRequest;
import com.caseware.engagement.model.DecisionType;
import com.caseware.engagement.model.UpdateStatus;
import com.caseware.engagement.service.EngagementQueryService;
import com.caseware.engagement.service.EngagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EngagementService engagementService;

    @Mock
    private EngagementQueryService engagementQueryService;

    @InjectMocks
    private DashboardController dashboardController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dashboardController).build();
    }

    @Test
    @DisplayName("Given tenantId When GET /engagements Then returns 200 OK with read model list")
    void givenTenantId_whenGetEngagements_thenReturnsReadModels() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID engagementId = UUID.randomUUID();
        EngagementReadModel view = new EngagementReadModel(
                engagementId, tenantId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                2026, UpdateStatus.PENDING_UPDATES);
        when(engagementQueryService.getEngagementsByTenant(tenantId)).thenReturn(List.of(view));

        mockMvc.perform(get("/api/v1/dashboard/engagements").param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].engagementId").value(engagementId.toString()))
                .andExpect(jsonPath("$[0].updateStatus").value("PENDING_UPDATES"));

        verify(engagementQueryService, times(1)).getEngagementsByTenant(tenantId);
    }

    @Test
    @DisplayName("Given engagementId When GET /engagements/{id} Then returns 200 OK")
    void givenEngagementId_whenGetEngagement_thenReturnsReadModel() throws Exception {
        UUID engagementId = UUID.randomUUID();
        EngagementReadModel view = new EngagementReadModel(
                engagementId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                2026, UpdateStatus.UPDATES_REVIEWED);
        when(engagementQueryService.getEngagement(engagementId)).thenReturn(view);

        mockMvc.perform(get("/api/v1/dashboard/engagements/{engagementId}", engagementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateStatus").value("UPDATES_REVIEWED"));
    }

    @Test
    @DisplayName("Given valid decision When POST /engagements/{id}/decisions Then returns 202 Accepted")
    void givenValidDecision_whenRecordDecision_thenAccepts() throws Exception {
        UUID engagementId = UUID.randomUUID();
        UUID targetVersionId = UUID.randomUUID();
        RecordDecisionRequest request =
                new RecordDecisionRequest(DecisionType.APPLIED, targetVersionId, "user-1", "looks good");

        mockMvc.perform(post("/api/v1/dashboard/engagements/{engagementId}/decisions", engagementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPLIED","targetVersionId":"%s","userId":"user-1","reason":"looks good"}
                                """.formatted(targetVersionId)))
                .andExpect(status().isAccepted());

        verify(engagementService, times(1)).recordDecision(
                engagementId, DecisionType.APPLIED, targetVersionId, "user-1", "looks good");
    }
}