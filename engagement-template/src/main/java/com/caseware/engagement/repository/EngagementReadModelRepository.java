package com.caseware.engagement.repository;

import com.caseware.engagement.model.EngagementReadModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EngagementReadModelRepository extends JpaRepository<EngagementReadModelEntity, UUID> {

    List<EngagementReadModelEntity> findByTenantId(UUID tenantId);
}