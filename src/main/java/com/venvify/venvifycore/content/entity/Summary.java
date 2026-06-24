package com.venvify.venvifycore.content.entity;

import com.venvify.venvifycore.common.entity.BaseEntity;
import com.venvify.venvifycore.content.enums.SummaryStatus;
import com.venvify.venvifycore.event.entity.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "summaries",
        uniqueConstraints = @UniqueConstraint(name = "uq_summary_event", columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Summary extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** Transcript lưu ở storage ngoài (S3/Spaces), DB chỉ giữ URL. */
    @Column(name = "transcript_url", length = 500)
    private String transcriptUrl;

    @Column(name = "summary_content", columnDefinition = "TEXT")
    private String summaryContent;

    /** Danh sách câu hỏi Q&A hay nhất, lưu JSON. */
    @Column(name = "top_questions", columnDefinition = "TEXT")
    private String topQuestions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SummaryStatus status;

    @Column(name = "model_used", length = 50)
    private String modelUsed;
}
