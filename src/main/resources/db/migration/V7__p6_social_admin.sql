-- V7: nền P6 social/admin (plan 20260702-p6 §6 — ledger cũ dự kiến "V9/V10" nhưng P6 kéo lên
-- trước P2 theo quyết định third-party-sau-cùng 2026-07-04; architecture §6 đã đánh lại).

-- ============ audit_logs — vết mọi mutation của admin (P6 §4) ============
-- Append-only như ledger_entries: không API sửa/xóa + trigger chặn từ tầng DB (pattern V5).
create table audit_logs (
    id               bigint       not null auto_increment,
    public_id        varchar(36)  not null,
    admin_id         bigint       not null,
    action           varchar(50)  not null,
    target_type      varchar(30)  not null,
    target_public_id varchar(36)  null,
    detail           text         null,
    created_at       datetime(6)  not null,
    updated_at       datetime(6)  not null,
    version          bigint       not null,
    primary key (id),
    constraint uq_audit_public_id unique (public_id),
    constraint fk_audit_admin foreign key (admin_id) references users (id),
    index idx_audit_admin (admin_id),
    index idx_audit_target (target_type, target_public_id)
) engine=InnoDB;

create trigger trg_audit_no_update before update on audit_logs
for each row signal sqlstate '45000' set message_text = 'audit_logs is append-only';

create trigger trg_audit_no_delete before delete on audit_logs
for each row signal sqlstate '45000' set message_text = 'audit_logs is append-only';

-- ============ reviews.hidden — moderation flag (P6 §2, admin hide/unhide) ============
alter table reviews add column hidden bit not null default 0;
