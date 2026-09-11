package com.caseware.engagement.service;

import com.caseware.engagement.dto.EngagementReadModel;
import com.caseware.engagement.model.EngagementReadModelEntity;
import com.caseware.engagement.model.UpdateStatus;
import com.caseware.engagement.repository.EngagementReadModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngagementQueryServiceImpl implements EngagementQueryService {

    private final EngagementReadModelRepository readModelRepository;

    @Override
    public List<EngagementReadModel> getEngagementsByTenant(UUID tenantId) {
        return readModelRepository.findByTenantId(tenantId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public EngagementReadModel getEngagement(UUID engagementId) {
        return readModelRepository.findById(engagementId)
                .map(this::toView)
                .orElseThrow(() -> new IllegalArgumentException("Engagement not found: " + engagementId));
    }

    private EngagementReadModel toView(EngagementReadModelEntity entity) {
        return new EngagementReadModel(
                entity.getId(),
                entity.getTenantId(),
                entity.getTemplateId(),
                entity.getCurrentVersionId(),
                entity.getLatestVersionId(),
                entity.getLastDecidedVersionId(),
                entity.getFiscalYear(),
                deriveUpdateStatus(entity));
    }

    static UpdateStatus deriveUpdateStatus(EngagementReadModelEntity entity) {
        if (entity.getLastDecidedVersionId() == null) {
            return UpdateStatus.PENDING_UPDATES;
        }
        return entity.getLastDecidedVersionId().equals(entity.getLatestVersionId())
                ? UpdateStatus.UPDATES_REVIEWED
                : UpdateStatus.PENDING_UPDATES;
    }
}