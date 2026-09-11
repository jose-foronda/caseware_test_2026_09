package com.caseware.engagement.repository;

import com.caseware.engagement.model.UpdateDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UpdateDecisionRepository extends JpaRepository<UpdateDecision, UUID> {
}