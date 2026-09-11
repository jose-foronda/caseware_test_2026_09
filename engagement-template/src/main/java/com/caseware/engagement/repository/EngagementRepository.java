package com.caseware.engagement.repository;

import com.caseware.engagement.model.Engagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EngagementRepository extends JpaRepository<Engagement, UUID> {
}