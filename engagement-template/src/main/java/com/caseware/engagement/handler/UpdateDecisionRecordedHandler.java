package com.caseware.engagement.handler;

import com.caseware.engagement.event.UpdateDecisionRecorded;
import com.caseware.engagement.model.DecisionType;
import com.caseware.engagement.model.Engagement;
import com.caseware.engagement.model.EngagementReadModelEntity;
import com.caseware.engagement.repository.EngagementReadModelRepository;
import com.caseware.engagement.repository.EngagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UpdateDecisionRecordedHandler {

    private final EngagementReadModelRepository readModelRepository;
    private final EngagementRepository engagementRepository;

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UpdateDecisionRecorded event) {
        EngagementReadModelEntity readModel = readModelRepository.findById(event.engagementId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Read model not found for engagement: " + event.engagementId()));

        readModel.setLastDecidedVersionId(event.targetVersionId());

        if (event.decision() == DecisionType.APPLIED) {
            readModel.setCurrentVersionId(event.targetVersionId());

            Engagement engagement = engagementRepository.findById(event.engagementId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Engagement not found: " + event.engagementId()));
            engagement.setCurrentVersionId(event.targetVersionId());
            engagementRepository.save(engagement);
        }

        readModelRepository.save(readModel);
    }
}