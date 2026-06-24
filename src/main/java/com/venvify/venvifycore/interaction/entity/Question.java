package com.venvify.venvifycore.interaction.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.interaction.enums.QuestionStatus;
import com.venvify.venvifycore.room.entity.Room;
import com.venvify.venvifycore.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_questions_room_status", columnList = "room_id, status"),
        @Index(name = "idx_questions_room_upvotes", columnList = "room_id, upvote_count")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asker_id", nullable = false)
    private User asker;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Counter denormalized số upvote. */
    @Column(name = "upvote_count", nullable = false)
    @Builder.Default
    private Integer upvoteCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuestionStatus status;

    @Column(name = "answered_at")
    private Instant answeredAt;
}
