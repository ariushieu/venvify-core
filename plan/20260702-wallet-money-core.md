# Plan: Money-core — sổ kép, mua vé bằng ví, refund/release escrow

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT · **Người duyệt:** chủ dự án
**Liên quan:** [`20260630-ticket-transfer-and-payment.md`](./20260630-ticket-transfer-and-payment.md) (D11/D12, luồng §3.1), [`20260624-erd-entity-design.md`](./20260624-erd-entity-design.md) (D2), CLAUDE.md §5 ("không sai một xu")

> ⚠️ Slice TIỀN — duyệt + chốt các `O-M*` ở §10 xong mới code. Bao gồm cả các fix từ đợt review entity 2026-07-02.

---

## 0. Phạm vi

**Trong slice này:**
1. Vá 6 lỗ hổng từ review entity (migration `V4` + chỉnh entity/repo) — §1.
2. Engine sổ kép: `LedgerService.postTransfer(...)` — mọi chuyển động tiền đi qua đúng 1 cửa — §2.
3. Mua vé bằng ví (luồng 3.1 plan cũ) — bỏ guard 400 ở `BookingService` — §3.1.
4. Refund: user tự hủy vé paid + event bị hủy — §3.2, §3.3.
5. Release escrow khi event ENDED → ví HOST + hũ COMMISSION — §3.4.
6. API ví: số dư + sao kê — §3.5. Top-up dev-only (tắt cứng ở prod) — §3.6.
7. Job đối soát (reconcile) định kỳ — §4.
8. Unit test đầy đủ cho mọi nghiệp vụ trên — §9.

**NGOÀI slice này (để slice sau):**
- **Sepay** (payment intent, VietQR, webhook, hũ SUSPENSE hoạt động thật) — slice kế tiếp.
- **Payout host rút tiền ra bank** (PAYOUT là chuyển khoản tay + admin đánh dấu) — sau Sepay.
- **Ticket transfer** (đã có plan riêng, chờ O1–O3).
- Enum `SEPAY`, `TICKET_RESALE` — thêm trong migration của slice tương ứng (kỷ luật: chỉ ALTER khi slice cần).

---

## 1. Fix từ review entity (2026-07-02)

| # | Vấn đề | Fix | Tầng |
|---|---|---|---|
| F1 | Append-only chỉ là Javadoc; `LedgerEntry` có `@Setter`, không gì chặn UPDATE/DELETE | `@org.hibernate.annotations.Immutable` + bỏ `@Setter` (giữ `@Builder`); 2 trigger MySQL `BEFORE UPDATE`/`BEFORE DELETE` → `SIGNAL 45000` | JPA + DB |
| F2 | Thiếu CHECK constraint MySQL 8 | `ledger.amount <> 0`; `txn.amount > 0`; `escrow: gross = commission + host_net AND commission >= 0 AND host_net >= 0` | DB |
| F3 | `TransactionType` thiếu loại cho bút toán đảo | += `REVERSAL` (ALTER native enum, xếp alphabet) | DB + enum |
| F4 | Thiếu mốc thời gian audit | `transactions.completed_at`; `escrow_holds.refunded_at`, `paid_out_at` | DB + entity |
| F5 | Sao kê sort `created_at` → tie trong cùng micro giây | Sort theo `id` (đơn điệu vì insert dưới lock ví) | Repo |
| F6 | `findByBookingId` trả `Optional` nổ khi booking refund-rồi-mua-lại | `findByBookingIdAndStatus(bookingId, HELD)`; service đảm bảo ≤ 1 hold HELD/booking (dưới khóa) | Repo + service |
| F7 | Chưa có đường lấy hũ hệ thống | `WalletRepository.findByAccountType(WalletAccountType)` (mỗi loại đúng 1 dòng, seed ở V2) | Repo |

> **KHÔNG** check `balance_cached >= 0` ở DB — hũ `BANK_CLEARING` âm là hợp lệ trong sổ kép. Chỉ guard ví `USER` không âm ở service (R14).

---

## 2. Engine sổ kép — `LedgerService`

Một cửa duy nhất cho mọi chuyển động tiền. Không service nào khác được tự insert `ledger_entries` hay sửa `balance_cached`.

```
LedgerService (wallet/service/)
├── postTransfer(txn, fromWalletId, toWalletId, amount, description)   // cặp 2 bút toán
└── postSplit(txn, fromWalletId, List<Leg(toWalletId, amount)>, desc)  // 1 nguồn → N đích (release escrow)
```

**Thuật toán `postTransfer` (chạy TRONG transaction của caller):**
1. Validate `amount > 0`, `from != to`.
2. Khóa các ví liên quan bằng `findByIdForUpdate` theo **thứ tự id TĂNG DẦN** (R13 — chống deadlock).
3. Nếu ví nguồn là `USER` và `balance_cached − amount < 0` → `BadRequestException("Insufficient wallet balance")` (R14).
4. Insert bút toán debit (`−amount`, `balance_after` = số dư mới) + credit (`+amount`, tương tự); cập nhật `balance_cached` cả 2 ví cùng transaction (D2).
5. Assert tổng các bút toán vừa ghi của txn = 0 trước khi trả về (R8 — defensive, lệch → ném exception → rollback).

`postSplit` tương tự nhưng 1 nguồn `−gross` + N đích, tổng leg = gross (validate trước khi ghi).

**Quy tắc chia commission (R17 — không sai một xu):**
- `commission = floor(gross × rate / 100)` ; `host_net = gross − commission`.
- Ví dụ rate 5%: gross 99.999đ → commission 4.999đ, host_net 95.000đ. Tổng luôn khớp gross.

**`transaction_ref` (nội bộ):** `"{PREFIX}-{UUIDv7}"` với PREFIX theo loại (`TKT`, `RFD`, `TOP`, `REL`). Slice Sepay sẽ thêm format ngắn alnum-only nhúng được vào nội dung CK — không đụng slice này.

---

## 3. Luồng nghiệp vụ

### 3.1 Mua vé bằng ví (sửa `BookingService.create`)
Thay guard `"Paid bookings are not supported yet"` (line 55) bằng đường paid. Giữ nguyên toàn bộ check hiện có (PUBLISHED, không tự mua vé mình, chống trùng, sold-out dưới khóa event D4).

Atomic (1 `@Transactional`, thứ tự khóa R13: event trước → ví sau):
1. Khóa event, check + tăng `claimed_slots` (như hiện tại).
2. Tạo `Transaction` (TICKET_PURCHASE, INTERNAL, SUCCESS, `completed_at = now`, ref `TKT-...`, user = buyer, event set).
3. `postTransfer(txn, ví buyer → hũ ESCROW, price)` — thiếu tiền → exception → rollback cả slot.
4. Tạo `EscrowHold` HELD: `gross = price`, `commission = floor(rate)`, `host_net = gross − commission`, `held_at = now`. Guard: đã tồn tại hold HELD cho booking → `ConflictException` (F6).
5. Booking CONFIRMED, `price_paid = price`, `purchase_txn = txn`, `booked_at = now`.

> Slice này mua bằng ví = trừ tiền NGAY nên **không cần** trạng thái RESERVED/`reserved_until` — cái đó thuộc luồng Sepay QR (chờ thanh toán async), để slice sau.

### 3.2 User tự hủy vé paid (sửa `BookingService.cancel`)
Giữ nguyên rule hiện có (chỉ attendee, chỉ RESERVED/CONFIRMED, **trước `start_time`**). Thêm nhánh paid (`price_paid > 0`):
1. Tạo `Transaction` (REFUND, INTERNAL, SUCCESS, ref `RFD-...`).
2. `postTransfer(txn, hũ ESCROW → ví buyer, gross)` — **hoàn 100% gross** (O-M2): commission chỉ thực thu khi release, tiền đang nằm nguyên trong ESCROW.
3. Hold → REFUNDED + `refunded_at`; booking → **REFUNDED** (thay vì CANCELLED — phân biệt được "hủy vé free" vs "đã hoàn tiền"); trả slot dưới khóa event như hiện tại.

### 3.3 Event bị hủy (hook vào `EventService` cancel)
Với mọi hold HELD của event: lặp từng hold → refund như §3.2 (mỗi hold 1 txn REFUND riêng, per-buyer). Booking tương ứng → REFUNDED. Chạy trong transaction của thao tác hủy event (số lượng attendee MVP nhỏ; sau này nếu event nghìn vé thì chuyển sang job async — ghi chú lại, không làm bây giờ).

### 3.4 Release escrow khi event ENDED
`EscrowReleaseJob` (`@Scheduled`, mỗi 15 phút, bật `@EnableScheduling`):
1. **Auto-end (O-M3):** event PUBLISHED/LIVE có `end_time < now` → set ENDED. (`EventStatus.ENDED` đã có trong enum nhưng chưa ai set — job này là nơi transition đầu tiên.)
2. Với từng event ENDED còn hold HELD: tạo `Transaction` (COMMISSION, INTERNAL, SUCCESS, ref `REL-...`, user = host) + `postSplit(txn, ESCROW → [ví HOST: host_net, hũ COMMISSION: commission])` → hold RELEASED + `released_at`.
3. Mỗi hold xử lý trong transaction riêng (1 hold fail không chặn các hold khác); idempotent tự nhiên vì chỉ quét status HELD.

> Host chưa có ví? Không xảy ra — mọi user đăng ký đều được tạo ví (auth slice đã làm). MVP release ngay khi ENDED, **chưa có dispute window** (ghi nhận, làm sau nếu cần).

### 3.5 API ví (`WalletController`, cần auth)
| Method | Path | Mô tả |
|---|---|---|
| GET | `/wallets/me` | số dư ví (balance_cached, currency) |
| GET | `/wallets/me/entries` | sao kê phân trang, sort `id DESC` (R15): amount, balance_after, description, created_at, txn type/ref |

### 3.6 Top-up dev-only (để test paid flow trước khi có Sepay)
`POST /wallets/me/topup {amount}` → txn TOPUP INTERNAL + `postTransfer(BANK_CLEARING → ví user)`.
- Gate bằng config `app.money.dev-topup-enabled` (default **false**) **VÀ** guard cứng: profile `prod` active → luôn 404 bất kể flag (R16). Không có đường in tiền ở prod — money-in thật duy nhất là Sepay (slice sau).

---

## 4. Job đối soát — `ReconciliationJob`

`@Scheduled` mỗi ngày (cron 03:17) + có thể gọi tay qua test. 4 bất biến:
1. `SUM(toàn bộ ledger_entries.amount) = 0`.
2. Mỗi ví: `balance_cached == SUM(ledger theo ví)` (query có sẵn `sumAmountByWalletId`).
3. Mỗi transaction: `SUM(ledger theo txn) = 0`.
4. Số dư hũ ESCROW `== SUM(gross_amount các hold HELD)`.

Lệch → log **ERROR** kèm chi tiết (wallet/txn id, expected vs actual). MVP dừng ở log; alert (email/Slack) để sau. Repo bổ sung 2 query gộp (per-wallet, per-txn) để job không N+1.

---

## 5. RULE mới (nối tiếp R1–R12 plan cũ)

- **R13 — Thứ tự khóa toàn cục:** row `events` trước → row `wallets` theo **id tăng dần**. Mọi luồng tiền tuân thủ, không ngoại lệ.
- **R14 — Ví USER không âm** (check ở service sau khi khóa); hũ hệ thống ĐƯỢC âm (BANK_CLEARING bản chất là gương của bank).
- **R15 — Sao kê/tra cứu ledger sort theo `id`**, không theo `created_at`.
- **R16 — Top-up dev tắt cứng ở prod** (guard profile, không tin mỗi flag).
- **R17 — Chia tiền:** commission floor, host_net = gross − commission; tổng leg luôn == gross (assert).
- **R18 — Mọi nghiệp vụ tiền = đúng 1 `@Transactional`**, không `REQUIRES_NEW` lồng nhau (tránh commit nửa chừng); `LedgerService` là participant, không tự mở transaction.

---

## 6. Migration `V4__money_core_hardening.sql` (phác thảo)

```sql
-- F1: ledger append-only — chặn từ tầng DB
CREATE TRIGGER trg_ledger_no_update BEFORE UPDATE ON ledger_entries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_entries is append-only';
CREATE TRIGGER trg_ledger_no_delete BEFORE DELETE ON ledger_entries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_entries is append-only';

-- F2: CHECK constraints (MySQL >= 8.0.16 enforce thật)
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_amount_nonzero CHECK (amount <> 0);
ALTER TABLE transactions   ADD CONSTRAINT chk_txn_amount_positive   CHECK (amount > 0);
ALTER TABLE escrow_holds   ADD CONSTRAINT chk_escrow_split
    CHECK (gross_amount = commission_amount + host_net_amount
           AND commission_amount >= 0 AND host_net_amount >= 0);

-- F3: enum += REVERSAL (danh sách mới xếp alphabet, khớp Hibernate)
ALTER TABLE transactions MODIFY COLUMN type
    enum('COMMISSION','PAYOUT','REFUND','REVERSAL','TICKET_PURCHASE','TOPUP') NOT NULL;

-- F4: timestamps audit
ALTER TABLE transactions ADD COLUMN completed_at DATETIME(6) NULL;
ALTER TABLE escrow_holds
    ADD COLUMN refunded_at DATETIME(6) NULL,
    ADD COLUMN paid_out_at DATETIME(6) NULL;
```

Ghi chú:
- Trigger 1-statement (không `BEGIN…END`) → Flyway chạy thẳng, không cần đổi delimiter.
- Entity phải thêm field khớp (`completedAt`, `refundedAt`, `paidOutAt`, enum `REVERSAL`) cùng commit — `ddl-auto: validate` + CI (contextLoads chạy Flyway thật) sẽ bắt nếu lệch.
- `REVERSAL` slice này chỉ *sẵn sàng* (enum + type), chưa có endpoint admin tạo bút toán đảo — công cụ xử lý sự cố, dùng tay qua service khi cần.

---

## 7. Config mới (`application.yaml`)

```yaml
app:
  money:
    ticket-commission-percent: 5      # O-M1 — chờ chốt
    dev-topup-enabled: false          # bật tay ở local; prod luôn tắt (R16)
```

---

## 8. File đụng tới

**Mới:** `wallet/service/LedgerService`, `wallet/service/EscrowService` (hold/refund/release), `wallet/service/ReconciliationJob`, `wallet/service/EscrowReleaseJob`, `wallet/controller/WalletController`, `wallet/dto/{WalletResponse, LedgerEntryResponse, TopupRequest}`, `wallet/mapper/WalletMapper`, migration `V4`.

**Sửa:** `LedgerEntry` (F1), `Transaction`/`EscrowHold`/`TransactionType` (F3/F4), `LedgerEntryRepository`/`EscrowHoldRepository`/`WalletRepository` (F5/F6/F7), `BookingService` (§3.1, §3.2), `EventService` (hook §3.3), `SecurityConfig` (nếu cần — `/wallets/**` đã sau auth mặc định), main class `@EnableScheduling`.

---

## 9. Test (unit, Mockito — mỗi API/nghiệp vụ đều có)

- `LedgerServiceTest`: transfer happy; thiếu tiền ví USER; hũ hệ thống được âm; thứ tự khóa id tăng dần (verify order); split rounding (99.999đ/5% → 4.999 + 95.000); tổng leg ≠ gross → exception; amount ≤ 0 → exception.
- `BookingServiceTest` (bổ sung): mua paid thành công (đủ bút toán + hold + CONFIRMED); thiếu tiền → rollback slot; hold HELD trùng → Conflict; hủy paid → refund 100% + REFUNDED + trả slot; hủy sau start_time vẫn bị chặn (giữ test cũ).
- `EscrowReleaseJobTest`: auto-end theo end_time; release đúng split; hold lỗi không chặn hold khác; idempotent (chạy 2 lần không release đôi).
- `ReconciliationJobTest`: phát hiện lệch từng bất biến (4 case) + case sạch.
- `WalletControllerTest` mức service: topup bị chặn khi flag off / profile prod.

---

## 10. OPEN — cần bạn chốt trước khi code

- **O-M1 — Commission vé:** platform thu bao nhiêu % giá vé khi release? Khuyến nghị **5%** MVP (config được, đổi không cần migration). → chốt %.
- **O-M2 — Refund khi user tự hủy:** hoàn **100%** trước `start_time`, sau `start_time` không cho hủy (rule hiện có). Không phí hủy MVP. → OK?
- **O-M3 — Event ENDED:** tự động ENDED khi quá `end_time` (job 15') + release ngay, chưa có dispute window. Sau này có thể thêm nút "End event" cho host + delay release. → OK cho MVP?
- **O-M4 — Top-up dev-only** gate flag + profile như §3.6 (prod không bao giờ có đường in tiền). → OK?

---

## 11. Thứ tự implement (sau khi duyệt)

1. Migration V4 + chỉnh entity/repo (F1–F7) — nền, chưa đổi hành vi.
2. `LedgerService` + test (engine trước, cô lập).
3. `EscrowService` + paid booking + refund (§3.1–3.3) + test.
4. `EscrowReleaseJob` + `ReconciliationJob` (§3.4, §4) + test.
5. `WalletController` + dev top-up (§3.5, §3.6) + test.

> Mỗi bước 1 commit riêng (per-feature); xong slice báo file đã đổi. Slice kế tiếp: **Sepay** (payment intent + VietQR + webhook idempotent + SUSPENSE).
