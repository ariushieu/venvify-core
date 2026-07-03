-- V6: P3 transfer + discovery (plan 20260702-p3-transfer-discovery §3 — ledger cũ dự kiến
-- "V7 (P3)" nhưng P2 Sepay bị đẩy ra sau third-party nên P3 lấy V6; architecture §6 đã đánh lại).

-- ============ ticket_transfers (plan 20260630 §5, module booking) ============
-- Vừa là offer chuyển nhượng vừa là bản ghi lịch sử. status VARCHAR theo policy V5.
-- KHÔNG index riêng (booking_id) — idx_tt_booking_status prefix (booking_id, status) phủ luôn;
-- R-T1 (1 PENDING/booking) enforce ở service dưới lock booking, index này phục vụ query đó.
create table ticket_transfers (
    id             bigint       not null auto_increment,
    public_id      varchar(36)  not null,
    booking_id     bigint       not null,
    from_user_id   bigint       not null,
    to_user_id     bigint       not null,
    price          bigint       not null default 0,
    status         varchar(30)  not null,
    transaction_id bigint       null,
    expires_at     datetime(6)  null,
    completed_at   datetime(6)  null,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    version        bigint       not null,
    primary key (id),
    constraint uq_ticket_transfer_public_id unique (public_id),
    constraint fk_tt_booking foreign key (booking_id) references bookings (id),
    constraint fk_tt_from    foreign key (from_user_id) references users (id),
    constraint fk_tt_to      foreign key (to_user_id) references users (id),
    constraint fk_tt_txn     foreign key (transaction_id) references transactions (id),
    index idx_tt_booking_status (booking_id, status),
    index idx_tt_to_status (to_user_id, status),
    index idx_tt_from (from_user_id),
    constraint chk_tt_price_nonnegative check (price >= 0)
) engine=InnoDB;

-- ============ bookings: đếm số lần pass (R2 — chống rửa vé lòng vòng) ============
alter table bookings add column transfer_count int not null default 0;

-- ============ FULLTEXT cho discovery (plan P3 §2.2) ============
-- InnoDB FULLTEXT, parser default (tiếng Việt tách bằng khoảng trắng đủ dùng);
-- innodb_ft_min_token_size mặc định 3 → q < 3 ký tự đi đường LIKE fallback ở service.
alter table events add fulltext index ft_events_title_desc (title, description);
