package com.caseware.engagement.service;

import com.caseware.engagement.event.UpdateDecisionRecorded;
import com.caseware.engagement.model.DecisionType;
import com.caseware.engagement.model.Engagement;
import com.caseware.engagement.model.EngagementReadModelEntity;
import com.caseware.engagement.model.UpdateDecision;
import com.caseware.engagement.repository.EngagementReadModelRepository;
import com.caseware.engagement.repository.EngagementRepository;
import com.caseware.engagement.repository.UpdateDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngagementServiceImpl implements EngagementService {

    private final EngagementRepository engagementRepository;
    private final EngagementReadModelRepository readModelRepository;
    private final UpdateDecisionRepository updateDecisionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void recordDecision(UUID engagementId, DecisionType decision, UUID targetVersionId,
                               String userId, String reason) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (targetVersionId == null) {
            throw new IllegalArgumentException("targetVersionId must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        Engagement engagement = engagementRepository.findById(engagementId)
                .orElseThrow(() -> new IllegalArgumentException("Engagement not found: " + engagementId));
        EngagementReadModelEntity readModel = readModelRepository.findById(engagementId)
                .orElseThrow(() -> new IllegalArgumentException("Read model not found for engagement: " + engagementId));

        UUID fromVersionId = readModel.getLastDecidedVersionId() != null
                ? readModel.getLastDecidedVersionId()
                : readModel.getCurrentVersionId();
        UUID latestVersionId = readModel.getLatestVersionId();

        if (latestVersionId.equals(fromVersionId)) {
            throw new IllegalArgumentException("No pending update for engagement: " + engagementId);
        }
        if (!targetVersionId.equals(latestVersionId)) {
            throw new IllegalArgumentException(
                    "Decision target " + targetVersionId + " does not match the latest version " + latestVersionId);
        }

        UpdateDecision record = new UpdateDecision();
        record.setEngagementId(engagementId);
        record.setTemplateId(engagement.getTemplateId());
        record.setFromVersionId(fromVersionId);
        record.setTargetVersionId(targetVersionId);
        record.setDecision(decision);
        record.setDecidedBy(userId);
        record.setReason(reason);
        record.setDecidedAt(Instant.now());
        updateDecisionRepository.save(record);

        eventPublisher.publishEvent(new UpdateDecisionRecorded(
                engagementId, engagement.getTenantId(), decision, targetVersionId, userId, Instant.now()));
    }
}