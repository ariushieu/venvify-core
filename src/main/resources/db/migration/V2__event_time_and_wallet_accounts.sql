-- V2: nền cho D13 (event time dự kiến/thực tế) + D12 (double-entry wallet accounts).
-- V1 đã đóng băng; đây là migration tăng dần đầu tiên.

-- ============ D13: events — thời gian dự kiến vs thực tế ============
-- start_time/end_time là giờ DỰ KIẾN; cho NULL khi DRAFT, service bắt buộc khi PUBLISH.
-- Giờ THỰC TẾ lấy từ rooms.started_at / rooms.ended_at.
alter table events
    modify column start_time datetime(6) null,
    modify column end_time   datetime(6) null,
    add column original_start_time datetime(6) null,
    add column timezone varchar(40) null;

-- ============ D12: wallets — double-entry + tài khoản hệ thống ============
-- account_type phân biệt ví user (USER) với các hũ hệ thống.
-- Cột là native ENUM (khớp cách Hibernate sinh @Enumerated(STRING) trên MySQL), giá trị xếp alphabet.
-- user_id nới lỏng sang NULL cho hũ hệ thống; UNIQUE(user_id) vẫn đúng vì MySQL coi nhiều NULL là khác nhau.
alter table wallets
    add column account_type enum('BANK_CLEARING','COMMISSION','ESCROW','SUSPENSE','USER') not null default 'USER',
    modify column user_id bigint null;

-- Seed 4 hũ hệ thống (mỗi loại 1 dòng, VND). public_id = UUID v4 (đủ cho hàng hệ thống).
insert into wallets (public_id, account_type, user_id, currency, balance_cached, created_at, updated_at, version)
values
    (uuid(), 'ESCROW',        null, 'VND', 0, now(6), now(6), 0),
    (uuid(), 'COMMISSION',    null, 'VND', 0, now(6), now(6), 0),
    (uuid(), 'BANK_CLEARING', null, 'VND', 0, now(6), now(6), 0),
    (uuid(), 'SUSPENSE',      null, 'VND', 0, now(6), now(6), 0);
