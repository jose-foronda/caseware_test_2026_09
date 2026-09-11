package com.caseware.engagement.service;

import com.caseware.engagement.dto.EngagementReadModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface EngagementQueryService {

    @Transactional(readOnly = true)
    List<EngagementReadModel> getEngagementsByTenant(UUID tenantId);

    @Transactional(readOnly = true)
    EngagementReadModel getEngagement(UUID engagementId);
}