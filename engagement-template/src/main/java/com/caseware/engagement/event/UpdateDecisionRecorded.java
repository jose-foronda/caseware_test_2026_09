package com.caseware.engagement.event;

import com.caseware.engagement.model.DecisionType;

import java.time.Instant;
import java.util.UUID;

public record UpdateDecisionRecorded(UUID engagementId,
                                     UUID tenantId,
                                     DecisionType decision,
                                     UUID targetVersionId,
                                     String userId,
                                     Instant timestamp) {
}