package com.venvify.venvifycore.interaction.repository;

import com.venvify.venvifycore.interaction.entity.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    List<PollOption> findByPollIdOrderByDisplayOrderAsc(Long pollId);
}
