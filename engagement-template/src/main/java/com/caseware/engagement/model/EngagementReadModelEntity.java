package com.caseware.engagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "engagement_read_model", schema = "em")
public class EngagementReadModelEntity {

    @Id
    @Column(name = "engagement_id", columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "template_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "current_version_id", columnDefinition = "uuid", nullable = false)
    private UUID currentVersionId;

    @Column(name = "latest_version_id", columnDefinition = "uuid", nullable = false)
    private UUID latestVersionId;

    @Column(name = "last_decided_version_id", columnDefinition = "uuid")
    private UUID lastDecidedVersionId;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;
}