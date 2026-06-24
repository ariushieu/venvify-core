package com.venvify.venvifycore.interaction.repository;

import com.venvify.venvifycore.interaction.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByPublicId(String publicId);

    Page<Question> findByRoomIdOrderByUpvoteCountDesc(Long roomId, Pageable pageable);
}
