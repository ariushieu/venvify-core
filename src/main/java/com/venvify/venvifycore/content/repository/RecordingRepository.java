package com.venvify.venvifycore.content.repository;

import com.venvify.venvifycore.content.entity.Recording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByPublicId(String publicId);

    Optional<Recording> findByEventId(Long eventId);
}
