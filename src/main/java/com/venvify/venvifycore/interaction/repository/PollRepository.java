package com.venvify.venvifycore.interaction.repository;

import com.venvify.venvifycore.interaction.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends JpaRepository<Poll, Long> {

    Optional<Poll> findByPublicId(String publicId);

    List<Poll> findByRoomIdOrderByCreatedAtAsc(Long roomId);
}
