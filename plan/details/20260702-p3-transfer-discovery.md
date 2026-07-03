# Plan kỹ thuật P3 — Chuyển nhượng vé + Discovery/Search

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ✅ ĐÃ DUYỆT 2026-07-04 (refresh §8 xong — xem amend log) · **Phase:** P3 (code 2026-07-04, TRƯỚC P2)
**Tiền đề:** ~~P2 xong (payment_intents + webhook Sepay chạy — transfer resale tái dùng hạ tầng đó)~~ ⛔ đảo thứ tự — xem amend log.
**Nền:** thiết kế transfer đã có ở `details/20260630-ticket-transfer-and-payment.md` (D8–D13, R1–R6, DDL `ticket_transfers` §5) — doc này KHÔNG lặp lại, chỉ chốt opens + bổ sung tầng kỹ thuật còn thiếu. Bám `master/20260702-technical-architecture.md`.

> **⛔ AMEND LOG (2026-07-04 — refresh đầu phase, user duyệt):**
> - **Thứ tự phase đảo:** user chốt "third-party để sau cùng" → P3 code TRƯỚC P2 Sepay. Hệ quả duy nhất: **§1.3 đường QR (purpose TRANSFER) DỜI về P2** — resale hiện chỉ đi **đường ví**; mọi phần khác của doc không phụ thuộc Sepay. (Prod chưa có đường nạp ví cho tới P2 nên resale có giá chưa dùng được ngoài prod — nhất quán với paid booking hiện tại, không phải regression.)
> - **§0 chốt opens:** O1 = 0% (`app.money.resale-commission-percent: 0`, code đi `postSplit` sẵn — bật phí sau chỉ đổi config) · O2 = 1 hop · O3 = chặn khi `now ≥ start_time`. Code vẫn tính commission theo config (leg 0 tự skip trong LedgerService).
> - **§3 migration:** số thật = **V6** (không phải V7 — P2 nhường số; master §6 đã đánh lại). Index `(booking_id, status)` thay cho `INDEX(booking_id)` đơn của DDL 20260630 §5 (prefix phủ được).
> - **§2.4 storefront basic:** LÀM trong đợt này (P6 gộp cùng — followerCount/avgRating có luôn, không để nullable chờ).
> - **Edge có chủ đích (phát hiện lúc refresh):** receiver còn booking-row **CANCELLED** của cùng event → accept bị 409 (UNIQUE(event_id, attendee_id) không cho đổi attendee trúng row trùng; không hard-delete row cũ vì dính FK tiền). Limitation MVP — ghi báo cáo, xử nếu gặp thật.
> - Notification/email 2 chiều (§1.2 AFTER_COMMIT): implement cùng đợt — module notification P6 làm ngay sau transfer, cùng chuỗi commit.

---

## 0. Chốt opens O1–O3 (plan 20260630 §6) — đề xuất

| # | Đề xuất | Ghi chú |
|---|---|---|
| **O1 — commission resale** | **0%** MVP | Khuyến khích pass đúng giá; config `app.money.resale-commission-percent=0` để bật sau không sửa code |
| **O2 — MAX_HOPS** | **1** | `app.booking.transfer-max-hops=1` |
| **O3 — cutoff** | chặn khi `now ≥ start_time` | Đơn giản, đủ; không buffer |

(O4 Sepay đã giải quyết ở P2. O5 refund-về-ví: đã chốt trong money-core — refund luôn về ví.)

---

## 1. Ticket transfer — bổ sung kỹ thuật

### 1.1 State machine `ticket_transfers.status`

```
PENDING ──accept+paid──▶ COMPLETED
   ├──sender hủy───────▶ CANCELLED
   ├──receiver từ chối─▶ DECLINED
   └──quá 72h──────────▶ EXPIRED (job)
```

**RULE bổ sung R-T1:** mỗi booking chỉ 1 transfer PENDING tại một thời điểm — enforce ở service dưới lock booking (MySQL không có partial unique index); index `(booking_id, status)`.

### 1.2 Hoàn tất transfer (atomic — 1 transaction, lock ordering master §7)

Lock booking `FOR UPDATE` → verify: booking CONFIRMED · `transfer_count < max-hops` · `now < event.start_time` · event không CANCELLED/ENDED · receiver chưa có booking event này (check trước cho lỗi đẹp — UNIQUE(event_id, attendee_id) vẫn là chốt chặn cuối) → mutate: `booking.attendee = to_user`, `transfer_count++`, transfer COMPLETED (+ `transaction_id` nếu resale). Escrow gốc KHÔNG đụng (D10). AFTER_COMMIT: notification 2 chiều + email.

> **Chính sách refund sau resale (có chủ đích — review 2026-07-02, chi tiết 20260630 §3.4):** event hủy → refund 100% `price_paid` GỐC từ escrow về **chủ vé hiện tại**, bất kể giá resale. Receiver mua rẻ hơn giá gốc sẽ nhận nhiều hơn số đã trả — phần chênh là thiệt của người bán lại (tự chấp nhận khi bán dưới giá); tiền resale P2P không truy hồi. R1 chặn chiều ngược. Test §6 phải assert đúng số này.

### 1.3 Thanh toán resale (price > 0)

- **Đường ví:** receiver accept → 1 transaction: `postTransfer` ví receiver → ví sender (txn `TICKET_RESALE`, ref `RSL-{uuidv7}`; commission O1 = 0% nên full price) + §1.2 cùng transaction.
- **Đường QR:** tái dùng `payment_intents` purpose **TRANSFER** (thêm hằng Java, không cần DDL — VARCHAR từ V4, master §6) — webhook topup-first vào ví receiver (P2 §2.5) rồi chạy đường ví trong cùng transaction. Mọi edge (thiếu/thừa/muộn) thừa kế nguyên si hành vi P2. ✨
- Tặng (price = 0): accept là complete luôn, không txn.

### 1.4 Edge cases phải test

Sender = receiver → 400 · receiver có vé rồi → 409 · 2 receiver accept race (lock booking + status check) · event cancel khi PENDING → listener `EventCancelledEvent` hủy transfer PENDING của event đó · sender cancel sau khi receiver đã trả (không xảy ra: trả và complete cùng transaction — PENDING không giữ tiền ai) · transfer vé đã check-in? — P3 chưa có check-in (P4); khi P4 vào thì thêm rule "đã ATTENDED không pass" (ghi vào R-T2, implement P4).

### 1.5 Endpoints

`POST /bookings/{publicId}/transfers` `{toUserEmail|toHandle, price}` (sender) · `GET /transfers/mine?role=sent|received&status=` · `POST /transfers/{publicId}/accept` (receiver — body optional `{paymentMethod: WALLET|QR}` khi price>0; QR → trả intent như P2) · `POST /transfers/{publicId}/decline` · `DELETE /transfers/{publicId}` (sender hủy PENDING).

---

## 2. Discovery / Search (public — không cần login)

### 2.1 Endpoint chính

`GET /events?q=&category=&priceType=free|paid&from=&to=&sort=upcoming|newest&page=&size=`
- Mặc định: status = PUBLISHED, `start_time > now`, sort `start_time ASC` (upcoming). `sort=newest` → `id DESC`.
- Filter kết hợp được; validate `from ≤ to`; size max 100 (master §4).
- Response: EventCard DTO (public_id, slug, title, cover, category, start/end, timezone, price, slots còn, host {handle, name, avatar}) — fetch join host, không N+1.

### 2.2 Search kỹ thuật

- `q` → MySQL **FULLTEXT** `MATCH(title, description) AGAINST(? IN BOOLEAN MODE)` với từng từ dạng `+từ*`; index `FULLTEXT ft_events_title_desc (title, description)` (V6 — InnoDB FULLTEXT, tiếng Việt tách bằng khoảng trắng nên parser default đủ; `innodb_ft_min_token_size=3` mặc định → q < 3 ký tự fallback `LIKE 'q%'` trên title).
- ⚠ Accent-insensitive ("lap trinh" tìm ra "lập trình") KHÔNG có với FULLTEXT + utf8mb4_unicode_ci trên InnoDB một cách đáng tin — chấp nhận hạn chế MVP (ghi báo cáo); không kéo Elasticsearch (master §14).
- EXPLAIN cả 2 đường (FULLTEXT / filter thuần) trước khi ship (master §6).

### 2.3 Trang chủ / category

- `GET /events/categories` — enum EventCategory + count PUBLISHED upcoming (query GROUP BY, cache).
- Section "sắp diễn ra"/"theo category" = cùng endpoint §2.1 với params — FE tự gọi, BE không cần endpoint riêng.
- **Cache Caffeine** (dependency `spring-boot-starter-cache` + caffeine): `@Cacheable` cho §2.1 khi **không có `q`** (search bypass) + categories count; key = params chuẩn hóa; TTL 60s, max 500 entries. Không invalidate chủ động — TTL đủ (sai lệch 60s chấp nhận).

### 2.4 Host storefront basic (cut-line #5 roadmap — làm nếu kịp trong P3, không thì P6)

`GET /hosts/{handle}` — profile public (name, avatar, bio) + events PUBLISHED (upcoming + past, paged). Follower count/rating để P6 đắp thêm vào cùng DTO (nullable).

---

## 3. Migration V6 (draft)

`V6__p3_transfer_discovery.sql`: `ticket_transfers` (DDL đã có ở plan 20260630 §5 — thêm cột theo state machine: `declined_at`? — dùng `status` + `updated_at` đủ, KHÔNG thêm) · `ALTER bookings ADD transfer_count INT NOT NULL DEFAULT 0` · `ALTER TABLE events ADD FULLTEXT ft_events_title_desc (title, description)`. (+TICKET_RESALE, +TRANSFER chỉ là hằng Java — cột VARCHAR từ V4, master §6.)

## 4. Config mới

```yaml
app:
  booking:
    transfer-max-hops: 1
    transfer-expiry-hours: 72
  money:
    resale-commission-percent: 0
```

## 5. Jobs mới

Transfer expiry (mỗi 15', PENDING quá `expires_at` → EXPIRED, notification sender) — thêm vào catalog master §8.

## 6. Test plan

| Nhóm | Ca |
|---|---|
| Transfer rules | R1 price ∈ [0, price_paid] · R2 hops · R3 cutoff O3 · R-T1 một PENDING/booking · sender=receiver · receiver đã có vé |
| Resale tiền | ví đủ/thiếu · QR đường P2 (thừa kế test topup-first) · SUM=0 · commission 0% đúng · REVERSAL không áp dụng (không refund transfer) |
| Race | 2 accept song song · accept vs sender-cancel · accept vs event-cancel |
| Event cancel | refund về **chủ hiện tại** sau transfer (kịch bản §3.4 plan 20260630 — test xuyên suốt: mua → pass → cancel → tiền về người nhận) |
| Discovery | filter matrix · FULLTEXT có/không dấu · q<3 fallback · cache hit (không q) / bypass (có q) · pagination max |

## 7. Điều kiện DONE của phase

Demo: A mua vé paid → pass cho B (tặng) và C mua lại từ D qua QR → host cancel event → tiền về đúng B/C. Trang discover lọc + tìm kiếm chạy trên prod với ≥20 event seed.

## 8. Refresh checklist đầu phase

- [ ] Chốt O1–O3 (§0) + rà lại R1–R6 plan 20260630 còn đúng không.
- [ ] Số migration V thật; kiểm enum ledger master §6 đã cập nhật giá trị V4/V5 (không cần DDL — VARCHAR).
- [ ] P2 payment_intents chạy prod ổn chưa (đường QR resale phụ thuộc).
- [ ] Quyết storefront basic làm trong P3 hay đẩy P6 (nhìn quỹ thời gian thực tế).
