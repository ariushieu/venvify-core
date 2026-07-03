package com.venvify.venvifycore.interaction.service;

import com.venvify.venvifycore.interaction.repository.PollRepository;
import com.venvify.venvifycore.interaction.repository.PollVoteRepository;
import com.venvify.venvifycore.interaction.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc-only cho analytics (P6 §5) — service đầu tiên của module interaction; nghiệp vụ
 * poll/Q&A thật vào P4. Trả 0 cho tới khi có room + data.
 */
@Service
@RequiredArgsConstructor
public class InteractionQueryService {

    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public long countPolls(Long eventId) {
        return pollRepository.countByRoomEventId(eventId);
    }

    @Transactional(readOnly = true)
    public long countPollVotes(Long eventId) {
        return pollVoteRepository.countByPollRoomEventId(eventId);
    }

    @Transactional(readOnly = true)
    public long countQuestions(Long eventId) {
        return questionRepository.countByRoomEventId(eventId);
    }
}
