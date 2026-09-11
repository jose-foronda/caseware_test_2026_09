package com.caseware.engagement.service;

import com.caseware.engagement.event.UpdateDecisionRecorded;
import com.caseware.engagement.model.DecisionType;
import com.caseware.engagement.model.Engagement;
import com.caseware.engagement.model.EngagementReadModelEntity;
import com.caseware.engagement.model.UpdateDecision;
import com.caseware.engagement.repository.EngagementReadModelRepository;
import com.caseware.engagement.repository.EngagementRepository;
import com.caseware.engagement.repository.UpdateDecisionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngagementServiceImplTest {

    private static final UUID ENGAGEMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("99999999-9999-9999-9999-999999999991");
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID V1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID V2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private EngagementReadModelRepository readModelRepository;

    @Mock
    private UpdateDecisionRepository updateDecisionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EngagementServiceImpl engagementService;

    private Engagement engagement() {
        Engagement engagement = new Engagement();
        engagement.setId(ENGAGEMENT_ID);
        engagement.setTenantId(TENANT_ID);
        engagement.setTemplateId(TEMPLATE_ID);
        engagement.setCurrentVersionId(V1);
        return engagement;
    }

    private EngagementReadModelEntity readModel(UUID current, UUID latest, UUID lastDecided) {
        EngagementReadModelEntity readModel = new EngagementReadModelEntity();
        readModel.setId(ENGAGEMENT_ID);
        readModel.setTenantId(TENANT_ID);
        readModel.setTemplateId(TEMPLATE_ID);
        readModel.setCurrentVersionId(current);
        readModel.setLatestVersionId(latest);
        readModel.setLastDecidedVersionId(lastDecided);
        return readModel;
    }

    @Test
    @DisplayName("Given APPLIED on latest pending update When recordDecision Then appends decision row and publishes event")
    void recordDecision_applied_appendsRowAndPublishesEvent() {
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(engagement()));
        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel(V1, V2, V1)));

        engagementService.recordDecision(ENGAGEMENT_ID, DecisionType.APPLIED, V2, "user-1", "applied");

        ArgumentCaptor<UpdateDecision> rowCaptor = ArgumentCaptor.forClass(UpdateDecision.class);
        verify(updateDecisionRepository).save(rowCaptor.capture());
        UpdateDecision row = rowCaptor.getValue();
        assertThat(row.getEngagementId()).isEqualTo(ENGAGEMENT_ID);
        assertThat(row.getTemplateId()).isEqualTo(TEMPLATE_ID);
        assertThat(row.getFromVersionId()).isEqualTo(V1);
        assertThat(row.getTargetVersionId()).isEqualTo(V2);
        assertThat(row.getDecision()).isEqualTo(DecisionType.APPLIED);
        assertThat(row.getDecidedBy()).isEqualTo("user-1");
        assertThat(row.getReason()).isEqualTo("applied");
        assertThat(row.getDecidedAt()).isNotNull();

        ArgumentCaptor<UpdateDecisionRecorded> eventCaptor = ArgumentCaptor.forClass(UpdateDecisionRecorded.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UpdateDecisionRecorded event = (UpdateDecisionRecorded) eventCaptor.getValue();
        assertThat(event.engagementId()).isEqualTo(ENGAGEMENT_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.decision()).isEqualTo(DecisionType.APPLIED);
        assertThat(event.targetVersionId()).isEqualTo(V2);
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Given DECLINED When recordDecision Then appends row and publishes without touching current version")
    void recordDecision_declined_appendsRowAndPublishesEvent() {
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(engagement()));
        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel(V1, V2, V1)));

        engagementService.recordDecision(ENGAGEMENT_ID, DecisionType.DECLINED, V2, "user-1", null);

        ArgumentCaptor<UpdateDecision> rowCaptor = ArgumentCaptor.forClass(UpdateDecision.class);
        verify(updateDecisionRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getDecision()).isEqualTo(DecisionType.DECLINED);
        assertThat(rowCaptor.getValue().getReason()).isNull();

        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<UpdateDecisionRecorded>any());
    }

    @Test
    @DisplayName("Given accumulated versions When recordDecision Then from_version is last decided, target is latest")
    void recordDecision_usesLastDecidedAsFromVersion() {
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(engagement()));
        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel(V1, V2, V1)));

        engagementService.recordDecision(ENGAGEMENT_ID, DecisionType.APPLIED, V2, "user-1", null);

        ArgumentCaptor<UpdateDecision> rowCaptor = ArgumentCaptor.forClass(UpdateDecision.class);
        verify(updateDecisionRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getFromVersionId()).isEqualTo(V1);
        assertThat(rowCaptor.getValue().getTargetVersionId()).isEqualTo(V2);
    }

    @Test
    @DisplayName("Given unknown engagement When recordDecision Then throws")
    void recordDecision_unknownEngagement_throws() {
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engagementService.recordDecision(
                ENGAGEMENT_ID, DecisionType.APPLIED, V2, "user-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Engagement not found");

        verify(updateDecisionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Given no pending update (latest equals last decided) When recordDecision Then throws")
    void recordDecision_noPendingUpdate_throws() {
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(engagement()));
        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel(V2, V2, V2)));

        assertThatThrownBy(() -> engagementService.recordDecision(
                ENGAGEMENT_ID, DecisionType.APPLIED, V2, "user-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No pending update");

        verify(updateDecisionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Given target not equal to latest version When recordDecision Then throws")
    void recordDecision_targetNotLatest_throws() {
        when(engagementRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(engagement()));
        when(readModelRepository.findById(ENGAGEMENT_ID)).thenReturn(Optional.of(readModel(V1, V2, V1)));
        UUID bogus = UUID.randomUUID();

        assertThatThrownBy(() -> engagementService.recordDecision(
                ENGAGEMENT_ID, DecisionType.APPLIED, bogus, "user-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match the latest version");

        verify(updateDecisionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Given null decision When recordDecision Then throws")
    void recordDecision_nullDecision_throws() {
        assertThatThrownBy(() -> engagementService.recordDecision(
                ENGAGEMENT_ID, null, V2, "user-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision must not be null");
    }
}