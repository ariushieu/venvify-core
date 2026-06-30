package com.venvify.venvifycore.event.entity;

import com.venvify.venvifycore.common.entity.SoftDeletableEntity;
import com.venvify.venvifycore.event.enums.EventCategory;
import com.venvify.venvifycore.event.enums.EventStatus;
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
@Table(name = "events", indexes = {
        @Index(name = "idx_events_host", columnList = "host_id"),
        @Index(name = "idx_events_status", columnList = "status"),
        @Index(name = "idx_events_start", columnList = "start_time"),
        @Index(name = "idx_events_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** SEO-friendly URL, duy nhất toàn hệ thống. */
    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private EventCategory category;

    /** Giờ dự kiến bắt đầu (D13). NULL khi DRAFT; bắt buộc khi PUBLISH (enforce ở service). */
    @Column(name = "start_time")
    private Instant startTime;

    /** Giờ dự kiến kết thúc, ước lượng (D13). Giờ thực tế lấy từ rooms.started_at/ended_at. */
    @Column(name = "end_time")
    private Instant endTime;

    /** Lịch gốc trước khi POSTPONE (D13). Set khi dời lịch lần đầu để FE hiện "dời từ X sang Y". */
    @Column(name = "original_start_time")
    private Instant originalStartTime;

    /** Múi giờ hiển thị của event (IANA, vd Asia/Ho_Chi_Minh). Lưu UTC, render theo tz này. */
    @Column(name = "timezone", length = 40)
    private String timezone;

    @Column(name = "max_slots", nullable = false)
    private Integer maxSlots;

    /** Counter số slot đã đặt (D4). Cập nhật cùng booking, có khóa row chống oversell. */
    @Column(name = "claimed_slots", nullable = false)
    @Builder.Default
    private Integer claimedSlots = 0;

    /** Giá vé tính bằng VND nguyên (D6). 0 = free. */
    @Column(name = "price_amount", nullable = false)
    @Builder.Default
    private Long priceAmount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;
}
