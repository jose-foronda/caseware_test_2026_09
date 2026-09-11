package com.caseware.engagement.dto;

import com.caseware.engagement.model.DecisionType;

import java.util.UUID;

public record RecordDecisionRequest(DecisionType decision,
                                    UUID targetVersionId,
                                    String userId,
                                    String reason) {
}