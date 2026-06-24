package com.venvify.venvifycore.interaction.repository;

import com.venvify.venvifycore.interaction.entity.QuestionUpvote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionUpvoteRepository extends JpaRepository<QuestionUpvote, Long> {

    boolean existsByQuestionIdAndUserId(Long questionId, Long userId);

    void deleteByQuestionIdAndUserId(Long questionId, Long userId);
}
