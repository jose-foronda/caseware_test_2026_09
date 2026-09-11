package com.caseware.engagement.dto;

import com.caseware.engagement.model.UpdateStatus;

import java.util.UUID;

public record EngagementReadModel(UUID engagementId,
                                  UUID tenantId,
                                  UUID templateId,
                                  UUID currentVersionId,
                                  UUID latestVersionId,
                                  UUID lastDecidedVersionId,
                                  int fiscalYear,
                                  UpdateStatus updateStatus) {
}