-- V5: nền money-core (plan 20260702-wallet-money-core §6 — file plan gọi là "V4",
-- nhưng số V4 đã bị V4__email_otp chiếm nên đánh V5; nội dung không đổi).

-- ============ F1: ledger append-only — chặn UPDATE/DELETE từ tầng DB ============
-- Trigger 1 statement (không BEGIN...END) để Flyway chạy thẳng, khỏi đổi delimiter.
create trigger trg_ledger_no_update before update on ledger_entries
for each row signal sqlstate '45000' set message_text = 'ledger_entries is append-only';

create trigger trg_ledger_no_delete before delete on ledger_entries
for each row signal sqlstate '45000' set message_text = 'ledger_entries is append-only';

-- ============ F2: CHECK constraints (MySQL >= 8.0.16 enforce thật) ============
-- KHÔNG check balance_cached >= 0: hũ BANK_CLEARING âm là hợp lệ trong sổ kép (R14
-- chỉ guard ví USER, làm ở service sau khi khóa row).
alter table ledger_entries add constraint chk_ledger_amount_nonzero check (amount <> 0);
alter table transactions   add constraint chk_txn_amount_positive   check (amount > 0);
alter table escrow_holds   add constraint chk_escrow_split
    check (gross_amount = commission_amount + host_net_amount
           and commission_amount >= 0 and host_net_amount >= 0);

-- ============ Enum storage = VARCHAR (quyết 2026-07-02, architecture §6) ============
-- Convert MỌI cột enum native → VARCHAR(30). Từ đây thêm giá trị enum = thêm hằng Java
-- + cập nhật enum ledger trong architecture doc, KHÔNG cần DDL. Kiểm kê bằng
-- grep "enum (" V1 + V2; events.category đã là varchar(50) từ V1 nên không đụng.
-- Đánh đổi chấp nhận: mất ràng buộc giá trị tầng DB — @Enumerated(STRING) chỉ ghi được
-- giá trị hợp lệ từ code; SQL tay phải cẩn thận.
alter table bookings      modify column status           varchar(30) not null;
alter table escrow_holds  modify column status           varchar(30) not null;
alter table events        modify column status           varchar(30) not null;
alter table notifications modify column type             varchar(30) not null;
alter table polls         modify column status           varchar(30) not null;
alter table questions     modify column status           varchar(30) not null;
alter table recordings    modify column status           varchar(30) not null;
alter table rooms         modify column status           varchar(30) not null;
alter table summaries     modify column status           varchar(30) not null;
alter table transactions  modify column payment_provider varchar(30) null;
alter table transactions  modify column status           varchar(30) not null;
alter table transactions  modify column type             varchar(30) not null;
alter table user_roles    modify column role             varchar(30);
alter table users         modify column status           varchar(30) not null;
alter table wallets       modify column account_type     varchar(30) not null default 'USER';

-- ============ F4: mốc thời gian audit ============
alter table transactions add column completed_at datetime(6) null;
alter table escrow_holds
    add column refunded_at datetime(6) null,
    add column paid_out_at datetime(6) null;
