package com.venvify.venvifycore.content.repository;

import com.venvify.venvifycore.content.entity.Summary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SummaryRepository extends JpaRepository<Summary, Long> {

    Optional<Summary> findByPublicId(String publicId);

    Optional<Summary> findByEventId(Long eventId);
}
