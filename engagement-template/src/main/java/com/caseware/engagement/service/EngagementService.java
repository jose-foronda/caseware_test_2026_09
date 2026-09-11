package com.caseware.engagement.service;

import com.caseware.engagement.model.DecisionType;

import java.util.UUID;

public interface EngagementService {

    void recordDecision(UUID engagementId, DecisionType decision, UUID targetVersionId,
                        String userId, String reason);
}