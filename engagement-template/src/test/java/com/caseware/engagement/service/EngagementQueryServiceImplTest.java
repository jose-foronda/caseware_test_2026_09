package com.caseware.engagement.service;

import com.caseware.engagement.dto.EngagementReadModel;
import com.caseware.engagement.model.EngagementReadModelEntity;
import com.caseware.engagement.model.UpdateStatus;
import com.caseware.engagement.repository.EngagementReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngagementQueryServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("99999999-9999-9999-9999-999999999991");
    private static final UUID V1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID V2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private EngagementReadModelRepository readModelRepository;

    @InjectMocks
    private EngagementQueryServiceImpl queryService;

    private static EngagementReadModelEntity readModel(UUID id, UUID latest, UUID lastDecided) {
        EngagementReadModelEntity entity = new EngagementReadModelEntity();
        entity.setId(id);
        entity.setTenantId(TENANT_ID);
        entity.setTemplateId(UUID.randomUUID());
        entity.setCurrentVersionId(V1);
        entity.setLatestVersionId(latest);
        entity.setLastDecidedVersionId(lastDecided);
        entity.setFiscalYear(2026);
        return entity;
    }

    @Test
    @DisplayName("Given last decided equals latest When deriveStatus Then UPDATES_REVIEWED")
    void deriveStatus_lastDecidedEqualsLatest_reviewed() {
        EngagementReadModelEntity entity = readModel(UUID.randomUUID(), V2, V2);

        assertThat(EngagementQueryServiceImpl.deriveUpdateStatus(entity))
                .isEqualTo(UpdateStatus.UPDATES_REVIEWED);
    }

    @Test
    @DisplayName("Given last decided older than latest When deriveStatus Then PENDING_UPDATES")
    void deriveStatus_lastDecidedOlderThanLatest_pending() {
        EngagementReadModelEntity entity = readModel(UUID.randomUUID(), V2, V1);

        assertThat(EngagementQueryServiceImpl.deriveUpdateStatus(entity))
                .isEqualTo(UpdateStatus.PENDING_UPDATES);
    }

    @Test
    @DisplayName("Given never decided When deriveStatus Then PENDING_UPDATES")
    void deriveStatus_neverDecided_pending() {
        EngagementReadModelEntity entity = readModel(UUID.randomUUID(), V2, null);

        assertThat(EngagementQueryServiceImpl.deriveUpdateStatus(entity))
                .isEqualTo(UpdateStatus.PENDING_UPDATES);
    }

    @Test
    @DisplayName("Given tenant When getEngagementsByTenant Then returns mapped views")
    void getEngagementsByTenant_returnsMappedViews() {
        UUID idPending = UUID.randomUUID();
        UUID idReviewed = UUID.randomUUID();
        when(readModelRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(readModel(idPending, V2, V1), readModel(idReviewed, V2, V2)));

        List<EngagementReadModel> views = queryService.getEngagementsByTenant(TENANT_ID);

        assertThat(views).hasSize(2);
        assertThat(views).extracting(EngagementReadModel::updateStatus)
                .containsExactly(UpdateStatus.PENDING_UPDATES, UpdateStatus.UPDATES_REVIEWED);
        assertThat(views).extracting(EngagementReadModel::engagementId)
                .containsExactly(idPending, idReviewed);
    }

    @Test
    @DisplayName("Given unknown engagement When getEngagement Then throws")
    void getEngagement_unknown_throws() {
        UUID id = UUID.randomUUID();
        when(readModelRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getEngagement(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Engagement not found");
    }
}