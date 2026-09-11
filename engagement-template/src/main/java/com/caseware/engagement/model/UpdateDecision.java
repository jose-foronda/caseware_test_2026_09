package com.caseware.engagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "em_update_decision", schema = "em")
public class UpdateDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "decision_id", columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "engagement_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID engagementId;

    @Column(name = "template_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "from_version_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID fromVersionId;

    @Column(name = "target_version_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID targetVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionType decision;

    @Column(name = "summary_id", columnDefinition = "uuid")
    private UUID summaryId;

    @Column(name = "decided_by", nullable = false)
    private String decidedBy;

    @Column
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}