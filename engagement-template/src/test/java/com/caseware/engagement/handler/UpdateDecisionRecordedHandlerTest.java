package com.caseware.engagement.handler;

import com.caseware.engagement.event.UpdateDecisionRecorded;
import com.caseware.engagement.model.DecisionType;
import com.caseware.engagement.model.Engagement;
import com.caseware.engagement.model.EngagementReadModelEntity;
import com.caseware.engagement.repository.EngagementReadModelRepository;
import com.caseware.engagement.repository.EngagementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateDecisionRecordedHandlerTest {

    private static final UUID ENGAGEMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID V1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID V2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private EngagementReadModelRepository readModelRepository;

    @Mock
    private EngagementRepository engagementRepository;

    @InjectMocks
    private UpdateDecisionRecordedHandler handler;

    private EngageReadModelBuilder readModel() {
        EngagementReadModelEntity readModel = new EngagementReadModelEntity();
        readModel.setId(ENGAGEMENT_ID);
        readModel.setCurrentVersionId(V1);
        readModel.setLatestVersionId(V2);
        readModel.setLastDecidedVersionId(V1);
        return new EngageReadModelBuilder(readModel);
    }

    private UpdateDecisionRecorded event(DecisionType decision) {
        return new UpdateDecisionRecorded(
                ENGAGEMENT_ID, UUID.randomUUID(), decision, V2, "user-1", Instant.now());
    }

    @Test
    @DisplayName("Given APPLIED event When handle Then read model and engagement move to target version")
    void applyEventHandler_movesReadModelAndEngagementToTarget() {
        EngagementReadModelEntity readModel = readModel().build();
        Engagement engagement = new Engagement();
        engagement.setId(ENGAGEMENT_ID);
        engagement.setCurrentVersionId(V1);

        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel));
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(engagement));

        handler.handle(event(DecisionType.APPLIED));

        assertThat(readModel.getCurrentVersionId()).isEqualTo(V2);
        assertThat(readModel.getLastDecidedVersionId()).isEqualTo(V2);
        assertThat(engagement.getCurrentVersionId()).isEqualTo(V2);
        verify(readModelRepository).save(readModel);
        verify(engagementRepository).save(engagement);
    }

    @Test
    @DisplayName("Given DECLINED event When handle Then only last decided moves; current version untouched")
    void declinedEventHandler_onlyAdvancesLastDecided() {
        EngagementReadModelEntity readModel = readModel().build();

        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel));

        handler.handle(event(DecisionType.DECLINED));

        assertThat(readModel.getCurrentVersionId()).isEqualTo(V1);
        assertThat(readModel.getLastDecidedVersionId()).isEqualTo(V2);
        verify(readModelRepository).save(readModel);
        verify(engagementRepository, never()).findById(ENGAGEMENT_ID);
        verify(engagementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Given event for missing read model When handle Then throws")
    void handle_missingReadModel_throws() {
        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(event(DecisionType.APPLIED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Read model not found");
    }

    private static final class EngageReadModelBuilder {
        private final EngagementReadModelEntity readModel;

        private EngageReadModelBuilder(EngagementReadModelEntity readModel) {
            this.readModel = readModel;
        }

        private EngagementReadModelEntity build() {
            return readModel;
        }
    }
}