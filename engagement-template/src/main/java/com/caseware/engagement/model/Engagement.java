package com.caseware.engagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "em_engagement", schema = "em")
public class Engagement {

    @Id
    @Column(name = "engagement_id", columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "client_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "tenant_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "template_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "current_version_id", columnDefinition = "uuid", nullable = false)
    private UUID currentVersionId;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    @Column(name = "location_key", nullable = false)
    private String locationKey;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}