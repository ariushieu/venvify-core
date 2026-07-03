package com.venvify.venvifycore.interaction.repository;

import com.venvify.venvifycore.interaction.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    /** Host analytics (P6 §5) — tổng lượt vote mọi poll của event. */
    long countByPollRoomEventId(Long eventId);
}
