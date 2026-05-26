package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, UUID> {
}
