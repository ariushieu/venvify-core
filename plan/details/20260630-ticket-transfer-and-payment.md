# Plan: Chuyển nhượng vé (transfer) & Thanh toán Sepay

**Ngày tạo:** 2026-06-30 · **Trạng thái:** ⏳ CHỜ DUYỆT · **Người duyệt:** chủ dự án
**Liên quan:** [`20260624-erd-entity-design.md`](../master/20260624-erd-entity-design.md) (ERD gốc), `SPEC.md` §5.6 (phần tiền)

> ⚠️ Bản thiết kế **bổ sung** vào schema đã có, CHƯA sinh code. Duyệt + chốt các `OPEN:` ở §6 xong mình mới tạo entity + migration `V2`.

---

## 0. Quyết định đã chốt (từ trao đổi)

- **D8 — Vé gắn 1 event:** giữ nguyên, `Booking` = vé. Không tạo entity `Ticket` riêng.
- **D9 — Pass vé: CHUYỂN TRỰC TIẾP** tới 1 người cụ thể (qua email/handle), KHÔNG marketplace công khai. Có handshake: người gửi tạo offer → người nhận chấp nhận (+ trả tiền nếu có giá).
- **D10 — Chống đầu cơ:** giá pass nằm trong `[0, price_paid]` (≤ giá gốc, cho thấp hơn). `price = 0` = tặng. Một cơ chế duy nhất cho cả tặng & bán lại.
- **D11 — Thanh toán khi ví không đủ:** dùng **Sepay**, hiện **QR quét đúng giá vé** (trả thẳng, không bắt buộc nạp ví trước). Sepay đọc biến động số dư ngân hàng → bắn webhook → khớp theo nội dung CK.
- **D12 — Quản lý tiền double-entry:** mọi chuyển động tiền = các bút toán cân bằng (tổng = 0), vắt qua các hũ `USER`/`ESCROW`/`COMMISSION`/`BANK_CLEARING`/`SUSPENSE`. Bất biến `SUM(ledger)=0` + đối soát bank. Chi tiết §1.
- **D13 — Event time dự kiến vs thực tế:** `start_time` (dự kiến, chính xác) + `end_time` (dự kiến, ước lượng) cho **NULL khi DRAFT**, **bắt buộc khi PUBLISH** (enforce ở service); thêm `original_start_time` (lịch gốc khi POSTPONE) + `timezone`. Giờ **thực tế** lấy từ `rooms.started_at/ended_at`.

---

## 1. Mô hình tài khoản — double-entry (D12)

> Phần tiền không được sai một xu (CLAUDE.md §5). Coi cả hệ thống là tập **hũ (tài khoản)**; tiền không tự sinh/mất, chỉ chảy giữa các hũ. Mỗi giao dịch = các bút toán cân bằng, **tổng = 0**.

**Các hũ — mở rộng bảng `wallets` bằng cột `account_type`:**
- `USER` — ví mỗi user (tiền user đã ở trong hệ thống). 1 ví/user.
- `ESCROW` — tiền vé đang giữ chờ event xong (đối soát với `escrow_holds`).
- `COMMISSION` — phí platform được hưởng.
- `BANK_CLEARING` (tên gọi nội bộ "NGOÀI") — **ranh giới với ngân hàng thật**. Tiền vào/ra hệ thống đi qua đây.
- `SUSPENSE` — tiền Sepay chưa khớp `transaction_ref`, chờ xử lý tay (không bao giờ tự cộng bừa).

> **Ví user KHÔNG phải NGOÀI** — nó là hũ nội bộ. Nạp tiền = `BANK_CLEARING → ví USER` (ví là đích, không phải ranh giới). NGOÀI chỉ xuất hiện khi tiền **thật sự ra/vào** bank (nạp, mua trực tiếp, rút).

**Bất biến (khóa an toàn):**
- Nội bộ: `SUM(toàn bộ ledger_entries.amount) = 0` — luôn đúng. Lệch → có bug, lộ ngay.
- Ngoài: tiền thật trong bank = `−(số dư BANK_CLEARING)` = tổng số dư các hũ còn lại. Job đối soát định kỳ.
- `balance_cached` mỗi ví = `SUM(ledger amount theo ví)`; `SUM(escrow HELD)` = số dư hũ `ESCROW`.

**Bảng dòng tiền (mọi nghiệp vụ map về cặp bút toán cân bằng):**

| Nghiệp vụ | Tiền chảy |
|---|---|
| Nạp ví (Sepay) | BANK_CLEARING → ví USER |
| Mua vé bằng ví (ví đủ) | ví USER → ESCROW |
| Mua vé trực tiếp (Sepay, ví thiếu) | BANK_CLEARING → ESCROW |
| Event xong (release) | ESCROW → ví HOST + COMMISSION |
| Event hủy (refund) | ESCROW → ví buyer |
| Host rút tiền (payout) | ví HOST → BANK_CLEARING |
| Pass vé có giá (resale) qua ví | ví buyer mới → ví seller (+ COMMISSION nếu thu phí) |
| Sepay tiền lạ không khớp | BANK_CLEARING → SUSPENSE |

> `escrow_holds` vẫn giữ — là **business view** per-booking (release/refund từng vé), đối soát với số dư hũ `ESCROW` (**accounting view**). Host vẫn nhận **đúng 1 lần/chỗ** dù vé đổi chủ. Idempotency Sepay nhờ `UNIQUE(transaction_ref)`.

---

## 2. Thay đổi schema

### 2.1 Bảng MỚI: `ticket_transfers` (module `booking/`)
Vừa là **offer chuyển nhượng** vừa là **bản ghi lịch sử** (audit, append-only sau khi COMPLETED).

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| booking_id | BIGINT | FK→bookings.id, NOT NULL | vé được chuyển |
| from_user_id | BIGINT | FK→users.id, NOT NULL | chủ hiện tại (người gửi) |
| to_user_id | BIGINT | FK→users.id, NOT NULL | người nhận (resolve từ email/handle) |
| price | BIGINT | NOT NULL, DEFAULT 0 | **RULE: 0 ≤ price ≤ booking.price_paid** |
| status | VARCHAR(20) | NOT NULL | PENDING/COMPLETED/CANCELLED/DECLINED/EXPIRED |
| transaction_id | BIGINT | FK→transactions.id, NULL | NULL nếu tặng (price=0) |
| expires_at | DATETIME(6) | NULL | TTL của offer |
| completed_at | DATETIME(6) | NULL | |
| created_at, updated_at, version | | | base |

- Index: `INDEX(booking_id)`, `INDEX(to_user_id, status)`, `INDEX(from_user_id)`.
- Enum mới `TicketTransferStatus` trong `booking/enums/`.

### 2.2 `bookings` — thêm cột
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| reserved_until | DATETIME(6) | NULL | TTL giữ chỗ khi RESERVED chờ thanh toán; hết hạn → nhả slot |
| transfer_count | INT | NOT NULL, DEFAULT 0 | đếm số lần đã pass; phục vụ RULE giới hạn hop |

### 2.3 `transactions` — thêm cột
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| qr_payload | VARCHAR(1000) | NULL | chuỗi/URL VietQR của Sepay |
| expires_at | DATETIME(6) | NULL | hạn của QR/payment intent |

> Liên kết txn↔booking: dùng sẵn `bookings.purchase_txn_id` (set ngay lúc reserve). Webhook: tìm txn theo `transaction_ref` → tìm booking theo `purchase_txn_id`. **Không** thêm cột `booking_id` vào transactions (tránh FK 2 chiều thừa).

### 2.4 Enum — thêm giá trị (⚠️ CẦN migration DDL — đính chính)
**Đính chính:** kiểm tra V1 thấy Hibernate sinh `@Enumerated(STRING)` trên MySQL thành **native `ENUM(...)`**, KHÔNG phải `VARCHAR` (vd `type enum('COMMISSION','PAYOUT',...)`). → Thêm giá trị enum **phải** `ALTER TABLE … MODIFY COLUMN … ENUM(...)` với danh sách mới (xếp **alphabet** để khớp Hibernate). Làm trong migration của slice tương ứng:
- `TransactionType` += `TICKET_RESALE` → `alter table transactions modify column type enum('COMMISSION','PAYOUT','REFUND','TICKET_PURCHASE','TICKET_RESALE','TOPUP') not null;`
- `PaymentProvider` += `SEPAY` → `alter table transactions modify column payment_provider enum('INTERNAL','MOMO','SEPAY','VNPAY');`
- `BookingStatus`: đã có `RESERVED` → không thêm.

### 2.5 `wallets` — double-entry (D12)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| account_type | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | USER/ESCROW/COMMISSION/BANK_CLEARING/SUSPENSE |
| user_id | BIGINT | đổi NOT NULL → **NULL** | hũ hệ thống không có user; `UNIQUE(user_id)` vẫn ok (MySQL coi nhiều NULL là khác nhau) |

- Seed **4 hũ hệ thống** (ESCROW, COMMISSION, BANK_CLEARING, SUSPENSE) bằng migration — mỗi loại 1 dòng, VND.
- `ledger_entries` **KHÔNG đổi cấu trúc** — double-entry = mỗi transaction ghi nhiều dòng (mỗi hũ 1 dòng) cộng lại = 0. Ràng buộc enforce ở **code** + **job kiểm tra bất biến**. (Đây là điểm hay: double-entry chủ yếu là *kỷ luật code* + vài dòng seed, không phải đập lại schema.)

### 2.6 `events` — thời gian dự kiến vs thực tế (D13)
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| start_time | DATETIME(6) | đổi NOT NULL → **NULL** | giờ **dự kiến** bắt đầu; bắt buộc khi PUBLISH (service enforce) |
| end_time | DATETIME(6) | đổi NOT NULL → **NULL** | giờ **dự kiến** kết thúc (ước lượng); thực tế ở `rooms` |
| original_start_time | DATETIME(6) | NULL | lịch gốc, set khi POSTPONE lần đầu |
| timezone | VARCHAR(40) | NULL | IANA (vd `Asia/Ho_Chi_Minh`); lưu UTC, hiển thị theo tz |

---

## 3. Dòng tiền (3 luồng)

> Mỗi lần "chuyển tiền" dưới đây = **một cặp bút toán cân bằng** theo bảng §1 (tổng = 0). `escrow_hold` là **business-view** đi kèm khi tiền vào hũ `ESCROW` (per-booking để release/refund), không thay cho bút toán.

### 3.1 Mua vé — ví ĐỦ tiền (đường nội bộ)
1. Giữ chỗ: `Booking` RESERVED + `reserved_until`.
2. Atomic: debit ví buyer (−price vào ledger) → tạo `escrow_hold` (HELD: gross/commission/host_net) → `Booking` CONFIRMED, `purchase_txn` = txn (TICKET_PURCHASE, INTERNAL, SUCCESS).

### 3.2 Mua vé — ví THIẾU → Sepay QR (đường ngoài, D11)
1. Giữ chỗ: `Booking` RESERVED + `reserved_until = now + TTL` (vd 10–15').
2. Tạo `Transaction` (TICKET_PURCHASE, **SEPAY**, PENDING, `transaction_ref`, `qr_payload`, `expires_at`). Hiện QR đúng giá vé. Set `booking.purchase_txn`.
3. User quét & trả → **Sepay webhook** (khớp `transaction_ref` nhúng trong nội dung CK).
4. Atomic + idempotent (guard `UNIQUE(transaction_ref)` + check status): txn → SUCCESS → tạo `escrow_hold` (HELD) → `Booking` CONFIRMED. **Ví buyer KHÔNG đụng.**
5. Hết TTL chưa trả → nhả slot, `Booking` CANCELLED, txn CANCELLED.

### 3.3 Chuyển nhượng vé (D9/D10)
1. Người gửi tạo `ticket_transfer` PENDING (`to_user`, `price ∈ [0, price_paid]`, `expires_at`). Vé vẫn của người gửi tới khi COMPLETED.
2. **Tặng (price=0):** người nhận chấp nhận → atomic: `booking.attendee = to_user`, transfer COMPLETED, `transfer_count++`.
3. **Bán lại (price>0):** người nhận trả tiền — qua ví (debit nhận / credit gửi trừ commission) **hoặc** Sepay QR (webhook → credit người gửi trừ commission). Trả thành công → atomic: `booking.attendee = to_user`, `transfer.transaction = txn`, COMPLETED, `transfer_count++`. Txn loại `TICKET_RESALE`.
4. **Escrow vé gốc KHÔNG đụng** — host vẫn nhận 1 lần. Tiền pass là P2P riêng.

### 3.4 Event hủy sau khi đã pass
- Refund escrow của chỗ đó về **chủ hiện tại** (`booking.attendee` = người mua cuối) → credit ví họ.
- Tiền resale P2P giữ nguyên (người bán đã thu của người mua). Host không nhận (event hủy).

---

## 4. RULE chống lạm dụng (đưa vào service, có test)
- **R1:** `0 ≤ transfer.price ≤ booking.price_paid` (chống phe vé). Vi phạm → `BadRequestException`.
- **R2:** `booking.transfer_count < MAX_HOPS` (mặc định **1**) — chống rửa vé lòng vòng.
- **R3:** Chỉ pass khi `booking.status = CONFIRMED` và **trước khi event bắt đầu** (hoặc trước `start_time − cutoff`).
- **R4:** Người nhận phải là user đã đăng ký (resolve email/handle). (Mời người chưa có tài khoản → `OPEN`, để sau.)
- **R5:** Webhook Sepay idempotent: cùng `transaction_ref` xử lý 1 lần (UNIQUE + check status trước khi ghi).
- **R6:** Reserve có TTL: job/lazy-check nhả slot khi `reserved_until < now` (ăn khớp slot counter D4 — chỉ RESERVED còn hạn mới tính vào `claimed_slots`).

**RULE phần tiền (double-entry — D12):**
- **R7:** `ledger_entries` chỉ **INSERT** (append-only). Sửa sai = ghi **bút toán đảo** (reversal), KHÔNG UPDATE/DELETE.
- **R8:** Mỗi thao tác tiền ghi **đủ cặp bút toán** vắt qua các hũ; kiểm tra `SUM(amount của transaction) = 0` **trước khi commit**, lệch → rollback.
- **R9:** Mọi mutation số dư ví chạy trong **1 DB transaction** + `SELECT … FOR UPDATE` trên row ví (chống race trừ tiền 2 lần).
- **R10:** Tiền Sepay không khớp `transaction_ref` → đẩy hũ **`SUSPENSE`**, KHÔNG tự cộng vào đâu; xử lý tay.
- **R11:** Release escrow chỉ khi `event.status = ENDED`; refund chỉ khi `CANCELLED`. Refund vé đã pass → **chủ hiện tại** (§3.4).
- **R12:** (sau khi có auth) ghi `created_by` cho mọi thao tác tiền để audit.

---

## 5. Migration (phác thảo — chốt DDL lúc implement)

> **Cập nhật:** đã tách migration theo nhóm. **`V2__event_time_and_wallet_accounts.sql`** (nền D12+D13) **đã tạo & implement** (events nullable + `original_start_time`/`timezone`; wallets `account_type` + nới `user_id` NULL + seed 4 hũ hệ thống). Phần dưới (`ticket_transfers`, cột `bookings`/`transactions`) để dành các file **V3+** đi cùng slice Booking/Payment/Transfer.

DDL gốc gộp (tham chiếu — sẽ rải vào V3+):
```sql
-- bookings
ALTER TABLE bookings
  ADD COLUMN reserved_until DATETIME(6) NULL,
  ADD COLUMN transfer_count INT NOT NULL DEFAULT 0;

-- transactions
ALTER TABLE transactions
  ADD COLUMN qr_payload VARCHAR(1000) NULL,
  ADD COLUMN expires_at DATETIME(6) NULL;

-- ticket_transfers (đối chiếu kiểu cột base với V1: id/public_id/created_at/updated_at/version)
CREATE TABLE ticket_transfers (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  public_id      VARCHAR(36)  NOT NULL,
  booking_id     BIGINT       NOT NULL,
  from_user_id   BIGINT       NOT NULL,
  to_user_id     BIGINT       NOT NULL,
  price          BIGINT       NOT NULL DEFAULT 0,
  status         VARCHAR(20)  NOT NULL,
  transaction_id BIGINT       NULL,
  expires_at     DATETIME(6)  NULL,
  completed_at   DATETIME(6)  NULL,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  version        BIGINT       NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_ticket_transfer_public_id UNIQUE (public_id),
  CONSTRAINT fk_tt_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
  CONSTRAINT fk_tt_from    FOREIGN KEY (from_user_id) REFERENCES users (id),
  CONSTRAINT fk_tt_to      FOREIGN KEY (to_user_id) REFERENCES users (id),
  CONSTRAINT fk_tt_txn     FOREIGN KEY (transaction_id) REFERENCES transactions (id),
  INDEX idx_tt_booking (booking_id),
  INDEX idx_tt_to_status (to_user_id, status),
  INDEX idx_tt_from (from_user_id)
) ENGINE=InnoDB;

-- wallets: double-entry (D12)
ALTER TABLE wallets
  ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'USER',
  MODIFY COLUMN user_id BIGINT NULL;
INSERT INTO wallets (public_id, account_type, user_id, currency, balance_cached, created_at, updated_at, version)
VALUES
  (UUID(), 'ESCROW',        NULL, 'VND', 0, NOW(6), NOW(6), 0),
  (UUID(), 'COMMISSION',    NULL, 'VND', 0, NOW(6), NOW(6), 0),
  (UUID(), 'BANK_CLEARING', NULL, 'VND', 0, NOW(6), NOW(6), 0),
  (UUID(), 'SUSPENSE',      NULL, 'VND', 0, NOW(6), NOW(6), 0);

-- events: time dự kiến vs thực tế (D13)
ALTER TABLE events
  MODIFY COLUMN start_time DATETIME(6) NULL,
  MODIFY COLUMN end_time   DATETIME(6) NULL,
  ADD COLUMN original_start_time DATETIME(6) NULL,
  ADD COLUMN timezone VARCHAR(40) NULL;
```
> Có thể tách thành nhiều file `V2`, `V3`… cho rõ từng nhóm thay đổi (transfer / double-entry / event-time) nếu muốn — Flyway áp theo thứ tự.
> V1 đóng băng — KHÔNG sửa. Enum thêm giá trị → không cần DDL (cột đã VARCHAR). `ddl-auto: validate` sẽ bắt nếu DDL lệch entity.

---

## 6. OPEN — cần bạn chốt trước khi code
- **O1 — Commission trên resale:** platform có thu phí khi bán lại không? Khuyến nghị MVP: **0%** (khuyến khích pass đúng giá), bật phí sau. → bạn chốt %.
- **O2 — MAX_HOPS:** giới hạn số lần pass mỗi vé. Khuyến nghị **1**. → giữ 1 hay khác?
- **O3 — Cutoff thời gian pass:** chặn pass trước event bao lâu? Khuyến nghị **chặn khi đã `start_time`** (đơn giản). → cần buffer (vd trước 1h) không?
- **O4 — Sepay tích hợp:** cần tài khoản Sepay + cấu hình webhook (endpoint công khai, secret verify). Endpoint nhận webhook + cơ chế khớp `transaction_ref` trong nội dung CK. → chốt khi vào slice thanh toán.
- **O5 — Refund ra ngân hàng:** vé mua qua Sepay khi refund → trả về **ví** (không auto chuyển khoản ra bank). User tự rút (PAYOUT) sau. OK chứ?

---

## 7. Thứ tự slice (sau khi duyệt)
Phụ thuộc: thanh toán & transfer cần biết "ai đang thao tác" → cần **auth** trước.

1. **(đã teed-up) Slice User + JWT/Security** — nền auth.
2. **Slice Event + Booking (mua bằng ví)** — luồng 3.1, slot counter + lock (D4), reserve TTL (R6).
3. **Slice Sepay** — payment intent + QR + webhook idempotent (luồng 3.2, R5, O4).
4. **Slice Ticket Transfer** — entity `ticket_transfers` + service/controller, handshake, R1–R4 (luồng 3.3–3.4).

> Mỗi slice xong báo file đã đổi; bạn xác nhận rồi sang slice kế (CLAUDE.md §7).
