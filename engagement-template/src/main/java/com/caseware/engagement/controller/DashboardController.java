package com.caseware.engagement.controller;

import com.caseware.engagement.dto.EngagementReadModel;
import com.caseware.engagement.dto.RecordDecisionRequest;
import com.caseware.engagement.service.EngagementQueryService;
import com.caseware.engagement.service.EngagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EngagementService engagementService;
    private final EngagementQueryService engagementQueryService;

    @GetMapping("/engagements")
    public ResponseEntity<List<EngagementReadModel>> getEngagements(@RequestParam UUID tenantId) {
        return ResponseEntity.ok(engagementQueryService.getEngagementsByTenant(tenantId));
    }

    @GetMapping("/engagements/{engagementId}")
    public ResponseEntity<EngagementReadModel> getEngagement(@PathVariable UUID engagementId) {
        return ResponseEntity.ok(engagementQueryService.getEngagement(engagementId));
    }

    @PostMapping("/engagements/{engagementId}/decisions")
    public ResponseEntity<Void> recordDecision(@PathVariable UUID engagementId,
                                               @RequestBody RecordDecisionRequest request) {
        engagementService.recordDecision(engagementId, request.decision(), request.targetVersionId(),
                request.userId(), request.reason());
        return ResponseEntity.noContent().build();
    }
}