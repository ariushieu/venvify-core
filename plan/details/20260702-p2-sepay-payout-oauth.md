# Plan kỹ thuật P2 — Tiền thật: Sepay, Payout, OAuth Google, Email nhắc lịch

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT (khung kỹ thuật — refresh đầu phase §11 trước khi code) · **Phase:** P2 (dự kiến T8/2026, theo roadmap §4)
**Tiền đề:** money-core (plan `20260702-wallet-money-core.md`, ĐÃ DUYỆT) đã implement xong — LedgerService `postTransfer`/`postSplit`, V4, escrow, release job chạy ổn. Prod URL public đã có (gate: Sepay webhook cần HTTPS công khai). User đã đăng ký Sepay + link tài khoản bank (ops §8 roadmap).
**Bám:** `master/20260702-technical-architecture.md` (mọi RULE §4–§8 áp dụng).

---

## 0. Đề xuất cần bạn CHỐT đầu phase (Đ-P2.*)

| # | Vấn đề | Đề xuất của mình | Lý do |
|---|---|---|---|
| **Đ-P2.1** | Vòng đời "chờ thanh toán": bảng `payment_intents` riêng, **thay cho** thiết kế cũ (transactions PENDING + cột `qr_payload`/`expires_at` — plan 20260630 §2.3) | **Dùng payment_intents** (amend plan cũ) | `transactions` là sổ tiền — chỉ ghi khi tiền THẬT về (luôn SUCCESS, trừ REVERSAL). Intent có vòng đời riêng (expiry, QR, hủy) không làm bẩn bảng tiền. Đỡ txn PENDING rác + đỡ 2 cột thừa. |
| **Đ-P2.2** | Tiền vào không khớp intent nào | Ledger `BANK_CLEARING → SUSPENSE`, txn type mới `SUSPENSE_HOLD`; admin resolve = `SUSPENSE_RESOLVE` về ví đúng chủ | Đúng R10 (plan 20260630); có dấu vết txn đàng hoàng thay vì "để đó" |
| **Đ-P2.3** | Số TK bank của host | Mã hóa AES-GCM app-level (key `BANK_ENC_KEY` env), lưu thêm `last4` plain để hiển thị | PII tài chính; ~30' code với 1 util. DB dump lộ cũng không lộ số TK |
| **Đ-P2.4** | Webhook sai số tiền so với intent | **"Topup-first"**: tiền khớp intent LUÔN vào ví user trước; mục đích (mua vé) chỉ thực hiện nếu đủ điều kiện; sai giá → tiền nằm ví + notify "bấm mua lại" | Không mất tiền ca nào, không cần SUSPENSE cho user đã định danh, mọi edge sụp về 1 luồng (xem §2.5) |
| **Đ-P2.5** | OAuth state | Dùng HttpSession mặc định của oauth2-client CHỈ trong handshake (cookie tạm, 1 instance) | Stateless hóa state phải custom repository — không đáng với 1 instance |
| **Đ-P2.6** | Re-book sau khi booking CANCELLED (schema `UNIQUE(event_id, attendee_id)` chặn INSERT mới) | **Reuse row**: UPDATE row CANCELLED của chính user đó → RESERVED (reset `reserved_until`, `price_paid` mới) dưới lock event | Giữ nguyên ràng buộc 1-user-1-booking/event; không nới UNIQUE, không xóa row (giữ FK txn/intent lịch sử). Chi tiết §2.9 |

---

## 1. Phạm vi & thứ tự slice trong phase

1. **Slice 2.1 — Payment intent + Sepay webhook + Top-up** (§2)
2. **Slice 2.2 — Mua vé qua QR (direct)** (§2.9 tạo RESERVED+intent · §2.5-B webhook) — cần 2.1
3. **Slice 2.3 — Payout + host bank account + thin-admin payout** (§3)
4. **Slice 2.4 — OAuth Google** (§4)
5. **Slice 2.5 — Email nhắc lịch + hóa đơn** (§5)
6. **Slice 2.6 — Sepay daily reconcile** (§2.7)

Mỗi slice: migration (nếu có) → entity/repo → service + test → controller + test → swagger check → commit riêng.

---

## 2. Sepay integration

### 2.1 Mô hình
Sepay theo dõi tài khoản ngân hàng của platform → có tiền vào là bắn **webhook JSON** về endpoint mình. Đối soát bằng **nội dung chuyển khoản** chứa mã intent. Sepay KHÔNG giữ tiền — tiền nằm ở bank platform; Sepay chỉ là "mắt".

Payload dự kiến (⚠ đối chiếu docs Sepay lúc code — §11): `id` (sepay txn id), `gateway`, `transactionDate`, `accountNumber`, `subAccount`, `content`, `transferType` (in/out), `transferAmount`, `accumulated`, `referenceCode`, `description`.

### 2.2 Bảng mới

**`payment_intents`** — vòng đời chờ thanh toán (Đ-P2.1):

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id, public_id, created_at, updated_at, version | | base |
| code | VARCHAR(12), UNIQUE NOT NULL | mã nhúng nội dung CK, base32 Crockford 8 ký tự, prefix `VNV` khi render |
| purpose | VARCHAR(20) NOT NULL | BOOKING/TOPUP; P3 thêm TRANSFER — không cần DDL (VARCHAR, master §6) |
| user_id | FK users NOT NULL | ai sẽ nhận tiền vào ví |
| booking_id | FK bookings NULL | khi purpose=BOOKING |
| amount | BIGINT NOT NULL | số tiền kỳ vọng |
| status | VARCHAR(20) NOT NULL | PENDING/COMPLETED/EXPIRED/CANCELLED |
| expires_at | DATETIME(6) NOT NULL | |
| completed_webhook_id | FK sepay_webhook_events NULL | trace |
| topup_txn_id / purpose_txn_id | FK transactions NULL | 2 chân: tiền vào ví + (nếu có) mua vé |

Index: UNIQUE(code), (user_id, status), (status, expires_at).

**`sepay_webhook_events`** — audit + idempotency tầng 1:

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id, created_at | | KHÔNG update — append |
| sepay_id | BIGINT UNIQUE NOT NULL | idempotency: INSERT bị dup → đã xử lý, trả 200 luôn |
| raw_payload | JSON NOT NULL | nguyên văn |
| transfer_amount | BIGINT NOT NULL | |
| content_normalized | VARCHAR(500) | sau chuẩn hóa |
| matched_intent_id | FK NULL | |
| status | VARCHAR(20) NOT NULL | PROCESSED/UNMATCHED/FAILED — FAILED → job retry §2.6 |
| error | VARCHAR(500) NULL | |

`bookings` thêm cột `reserved_until DATETIME(6) NULL` (từ plan 20260630 §2.2 — cột `transfer_count` để V6). `transactions` KHÔNG thêm cột (Đ-P2.1). Giá trị enum mới (PaymentProvider +SEPAY; TransactionType +SUSPENSE_HOLD/+SUSPENSE_RESOLVE) KHÔNG cần DDL — cột đã VARCHAR từ V4 (master §6); chỉ thêm hằng Java + cập nhật enum ledger.

### 2.3 Webhook endpoint

`POST /webhooks/sepay` — permitAll ở SecurityConfig, tự xác thực:
1. Verify header `Authorization: Apikey {SEPAY_API_KEY}` bằng `MessageDigest.isEqual` (constant-time). Sai → 401, KHÔNG lưu.
2. `transferType != in` → lưu event PROCESSED (ghi nhận tiền ra, không xử lý) → 200.
3. INSERT `sepay_webhook_events` theo `sepay_id` — duplicate key → **200 ngay** (Sepay retry).
4. Xử lý đồng bộ trong cùng request (chỉ DB, nhanh): §2.5. Lỗi bất ngờ → event FAILED + vẫn **200** (đã persist, job §2.6 retry) — không để Sepay retry-storm.

**RULE:** endpoint này không log `raw_payload` ở INFO; không nằm trong rate-limit zone auth.

### 2.4 Matching

- Chuẩn hóa content: uppercase → bỏ mọi ký tự ngoài A-Z0-9 (bank hay chèn/cắt khoảng trắng, dấu).
- Tìm pattern `VNV[0-9A-Z]{8}` → tra `payment_intents.code`, status PENDING, chưa quá `expires_at` (nới grace 10' — tiền đến muộn vẫn nhận về ví, xem §2.5-C).
- Không thấy mã / không khớp intent nào → **SUSPENSE** (Đ-P2.2): txn `SUSPENSE_HOLD` (ref `SUS-{uuid}`) + ledger `BANK_CLEARING − / SUSPENSE +`, event UNMATCHED. Admin resolve (P6, tạm thời xử lý tay qua SQL có script sẵn): `SUSPENSE_RESOLVE` chuyển về ví đúng chủ.

### 2.5 Luồng xử lý khi khớp intent — nguyên tắc **"topup-first"** (Đ-P2.4)

Mọi webhook khớp intent chạy MỘT transaction DB, lock theo thứ tự master §7:

**Bước chung (mọi purpose):** txn TOPUP (ref `TOP-{uuidv7}`, provider SEPAY, provider_txn_id = sepay_id, SUCCESS) + ledger `BANK_CLEARING −X / USER(user_id) +X` với **X = transferAmount thật nhận được** (không phải amount kỳ vọng). Intent ghi `topup_txn_id`.

**A. purpose = TOPUP:** xong bước chung → intent COMPLETED → notification + email biên nhận (AFTER_COMMIT).

**B. purpose = BOOKING:** sau bước chung, trong CÙNG transaction:
- Lock booking `FOR UPDATE`: status RESERVED, còn hạn (hoặc CONFIRMED rồi → chỉ dừng ở topup, hiếm).
- Nếu ví đủ `price_paid` (vừa topup xong thì thường đủ): chạy đúng luồng mua vé nội bộ money-core §3.1 — txn TICKET_PURCHASE + `postSplit` USER→ESCROW + escrow_hold HELD + booking CONFIRMED. Intent COMPLETED, ghi `purpose_txn_id`.
- Nếu KHÔNG đủ (user trả thiếu): dừng ở topup. Intent COMPLETED (tiền đã về ví). Notification "tiền đã vào ví, còn thiếu Y — bấm thanh toán lại bằng ví trước khi hết giữ chỗ".
- Trả thừa: phần thừa tự nhiên nằm lại ví sau khi trừ giá vé — không cần xử lý riêng. ✨
- Về mặt ledger: đường QR = TOPUP + TICKET_PURCHASE atomic → **giữ nguyên RULE SPEC "mọi luồng tiền qua ví"**, sao kê user nhìn thấy 2 dòng minh bạch. (Thu hồi amend "QR thẳng vào escrow" trong roadmap §5 — không cần nữa.)

**C. Tiền đến sau khi intent EXPIRED/CANCELLED:** vẫn bước chung (tiền về ví user — intent lưu user_id nên định danh được) + notification. KHÔNG confirm booking (slot có thể đã nhả).

### 2.6 Job liên quan

- **Booking hold expiry** (1'): RESERVED quá `reserved_until` → lock event → `claimed_slots--` → booking CANCELLED + intent BOOKING đi kèm → EXPIRED, cùng 1 transaction (kỷ luật counter §2.9).
- **Intent expiry** (5'): quét PENDING quá `expires_at` còn sót → EXPIRED (thực tế chủ yếu là intent TOPUP — intent BOOKING đã được job trên xử cùng booking).
- **Webhook retry** (5'): event FAILED, attempts < 3 → chạy lại §2.5 (idempotent nhờ state check dưới lock); quá 3 → Sentry + để admin.

### 2.7 Daily reconcile với Sepay (04:05)

Gọi API list transactions của Sepay (qua `SepayClient` interface) cho ngày hôm trước → đối chiếu `sepay_webhook_events`: giao dịch có ở Sepay mà thiếu ở mình → xử lý như webhook đến muộn (cùng code path); có ở mình mà thiếu ở Sepay → sự cố P0: **email admin NGAY** (`app.ops.admin-email` — như reconcile ledger nội bộ, master §9) + Sentry (không được xảy ra). Kết quả ghi log + notification admin.

### 2.8 Top-up API

- `POST /wallets/me/topup-intents` `{amount}` (min `app.payment.topup-min` = 10.000đ, max 20.000.000đ/lần) → intent PENDING + trả `{code, qrImageUrl, amount, expiresAt, bankInfo}`.
- QR: dựng URL `img.vietqr.io/image/{bank}-{account}-compact2.png?amount=X&addInfo=VNV{code}` — không phụ thuộc API Sepay, chuẩn VietQR mọi app bank quét được.
- `GET /wallets/me/topup-intents/{publicId}` — FE poll trạng thái (MVP poll 3s; đẩy WS để P4 khi có hạ tầng STOMP).
- Dev-topup (money-core §3.6) giữ nguyên — double-gated, chỉ dev.

### 2.9 Slice 2.2 — luồng TẠO booking RESERVED + intent (bổ sung theo review 2026-07-02)

> §2.5-B mới chỉ tả phía webhook trên booking RESERVED **có sẵn** — mục này chốt RESERVED sinh ra thế nào, slot tính ra sao, user bỏ ngang thì sao.

**Endpoint:** mở rộng `BookingService.create` (money-core §3.1) với `paymentMethod: WALLET | QR`. WALLET = luồng money-core nguyên trạng (CONFIRMED ngay trong 1 transaction — RESERVED không persist). Đường **QR**, 1 transaction (lock ordering master §7 — event trước):

1. Khóa event `FOR UPDATE` → validate như luồng ví (PUBLISHED, không mua vé mình, chưa start, còn slot).
2. **`claimed_slots++` NGAY** — RESERVED chiếm slot thật từ lúc tạo (chống oversell trong lúc chờ tiền; đánh đổi: giữ chỗ tối đa 15' — chấp nhận).
3. Booking **RESERVED**, `reserved_until = now + booking-hold-minutes (15')`, `price_paid` = giá chốt tại thời điểm này.
4. Intent PENDING purpose=BOOKING, `amount = price_paid`, **`expires_at = reserved_until`** (MỘT mốc duy nhất, không lệch nhau).
5. Trả `{booking, intent: {code, qrImageUrl, amount, expiresAt}}`.

**Kỷ luật slot counter (đặc tả chính xác cho R6 plan 20260630):** `claimed_slots` là counter tăng/giảm tường minh dưới lock event — KHÔNG phải "đếm lại các booking còn hạn":
- `++` đúng 1 lần khi chiếm chỗ (tạo RESERVED, hoặc CONFIRMED thẳng ở đường ví/free).
- `--` đúng 1 lần khi nhả (hold-expiry job §2.6, hoặc user hủy RESERVED).
- Confirm (RESERVED → CONFIRMED, webhook §2.5-B) **không đổi** counter.
- Gặp full khi tạo booking mới: trước khi trả 409, quét-nhả ngay các RESERVED quá hạn của CHÍNH event đó (đang giữ lock sẵn) — người mua không phải đợi job 1'.

**Các đường thoát của RESERVED:**
- Tiền về đúng lúc → §2.5-B (CONFIRMED, hoặc dừng ở topup nếu thiếu).
- User bấm hủy: `DELETE /bookings/{publicId}` khi RESERVED → 1 transaction nhả slot + booking CANCELLED + intent CANCELLED. Tiền đến sau đó → §2.5-C (về ví).
- Bỏ ngang (đóng tab): không cần làm gì — hold-expiry job 1' dọn (§2.6).
- Trả thiếu (đã topup ca §2.5-B): bấm "thanh toán bằng ví" → `POST /bookings/{publicId}/pay` — chạy confirm-bằng-ví money-core trên CHÍNH booking RESERVED này (không tạo booking mới, không đụng slot).
- Đang RESERVED còn hạn mà gọi mua lần nữa → trả lại booking + intent PENDING hiện có (get-or-create, không đúp intent).

**Re-book sau CANCELLED (Đ-P2.6):** `UNIQUE(event_id, attendee_id)` chặn INSERT mới → reuse row: UPDATE row CANCELLED → RESERVED dưới lock event (reset `reserved_until`, `price_paid`). Row REFUNDED không reuse (chỉ sinh ra khi event đã hủy — không thể mua lại). ⚠ Refresh lúc code: đối chiếu hành vi hủy/mua-lại vé free hiện có trong `BookingService` để thống nhất một cơ chế.

**Race:** webhook §2.5-B vs hold-expiry vs user-hủy đều lock booking `FOR UPDATE` + check status trước khi mutate (pattern master §7) — kẻ đến sau thấy state mới: expiry thấy CONFIRMED → skip; webhook thấy CANCELLED → chỉ topup về ví (ca C).

---

## 3. Payout (rút tiền host)

### 3.1 Bảng mới

**`host_bank_accounts`** — 1 tài khoản/user (MVP): user_id FK UNIQUE · bank_bin VARCHAR(10) · bank_name VARCHAR(100) · account_number_enc VARBINARY(512) (AES-GCM, Đ-P2.3) · account_last4 VARCHAR(4) · account_holder_name VARCHAR(150) · verified BOOLEAN default false (admin đánh sau lần chuyển đầu thành công).

**`payout_requests`**: user_id FK · amount BIGINT · status VARCHAR(20) (REQUESTED/PAID/REJECTED) · bank_snapshot (bank_bin, last4, holder_name — copy lúc request) · txn_id FK · reversal_txn_id FK NULL · admin_id FK NULL · admin_note VARCHAR(500) · processed_at. Index (status, created_at), (user_id).

### 3.2 Dòng tiền — trừ ví NGAY khi request

- `POST /wallets/me/payout-requests` `{amount}` (min `app.money.payout-min-amount` = 50.000đ; phải có bank account): 1 transaction — lock ví host, kiểm `balance ≥ amount`, txn PAYOUT (ref `PAY-{uuidv7}`, SUCCESS) + ledger `USER(host) −amount / BANK_CLEARING +amount`, tạo request REQUESTED.
  - `BANK_CLEARING` dương = "nợ phải chi ra bank" — đúng ngữ nghĩa clearing; đối xứng với TOPUP (âm khi tiền vào). Không txn PENDING, không hũ mới.
- Admin đánh **PAID** (đã chuyển khoản tay + đính note/mã CK): chỉ đổi status + `processed_at` — KHÔNG bút toán (tiền đã ghi lúc request). Notification + email hóa đơn cho host.
- Admin **REJECT** (sai TK, nghi vấn): bút toán đảo REVERSAL hoàn ví (money-core §1) + status REJECTED + lý do → notification.
- User hủy request khi còn REQUESTED: cũng đi đường REVERSAL như reject (tự phục vụ).

### 3.3 Endpoints

Host: `PUT /users/me/bank-account` · `GET /users/me/bank-account` (trả last4) · `POST|GET /wallets/me/payout-requests` · `DELETE /wallets/me/payout-requests/{id}` (hủy khi REQUESTED).
Thin-admin (chỉ payout, full admin ở P6): `GET /admin/payout-requests?status=` · `PATCH /admin/payout-requests/{id}` `{action: PAID|REJECTED, note}` — `@PreAuthorize ADMIN`; seed 1 user ADMIN qua migration/SQL tay (ghi runbook).

---

## 4. OAuth Google

Dependency: `spring-boot-starter-oauth2-client` (kiểm artifact dòng Boot 4).

**Flow (BE-centric, SPA không cầm client secret):**
1. FE mở `GET {api}/auth/oauth2/google` → redirect Google (state trong HttpSession tạm — Đ-P2.5).
2. Callback về BE → lấy profile (`sub`, `email`, `email_verified`, `name`, `picture`).
3. Resolve user:
   - Có user theo `(oauth_provider, oauth_provider_id)` → login.
   - Chưa có, email khớp user local: chỉ **link** khi user local `email_verified = true` (set oauth fields); chưa verified → redirect FE với `error=account_exists_unverified` (chống takeover — master §5).
   - Chưa có gì → tạo user mới: email_verified=true, password NULL, role ATTENDEE, avatar từ Google.
   - `SUSPENDED` → redirect FE `error=unauthorized` (generic).
4. Phát **one-time login code** (bảng `auth_login_codes`: code 43 ký tự urlsafe random, user_id, expires 60s, used_at NULL, UNIQUE(code)) → redirect `{app.frontend-url}/auth/callback?code=...`
5. FE `POST /auth/oauth2/exchange {code}` → verify (chưa dùng, còn hạn — đánh dấu used trong UPDATE có điều kiện, chống race) → trả cặp access+refresh chuẩn như login thường.

**RULE:** không bao giờ đặt access/refresh token trên URL. Code 1 lần, 60s. Kiểm tra refresh rotation sẵn có (master §5 ⚠) trong slice này.

---

## 5. Email nhắc lịch + hóa đơn (Resend sẵn có)

- **Bảng `event_reminders`**: event_id FK · kind VARCHAR(10) (T24H/T1H) · sent_at · `UNIQUE(event_id, kind)`.
- **Job 5'**: events PUBLISHED, `start_time` trong cửa sổ `[now, now + 24h]` (kind T24H) / `[now, now + 1h]` (T1H), chưa có row reminder → INSERT row (chốt idempotency bằng UNIQUE) rồi AFTER_COMMIT gửi email cho mọi booking CONFIRMED (loop `@Async`, fail lẻ log + Sentry, chấp nhận không retry per-mail ở MVP — trade-off ghi rõ).
  - App chết qua mốc → cửa sổ kiểu "còn lại ≤ 24h/1h và chưa gửi" nên khi sống lại vẫn gửi (muộn còn hơn không), không đúp nhờ UNIQUE.
- **Hóa đơn/biên nhận:** listener AFTER_COMMIT của topup COMPLETED, booking CONFIRMED (paid), payout PAID → template email tương ứng (NotificationType đã có PAYMENT_RECEIPT, BOOKING_CONFIRMED).
- Template: text/HTML đơn giản trong code (Thymeleaf KHÔNG cần — string template đủ, đỡ dependency).

---

## 6. Migration V5 (draft — chốt DDL lúc code)

Một file `V5__p2_real_money.sql`: `payment_intents`, `sepay_webhook_events`, `host_bank_accounts`, `payout_requests`, `event_reminders`, `auth_login_codes`, `ALTER bookings ADD reserved_until`. **KHÔNG còn ALTER enum** — mọi cột enum đã VARCHAR từ V4 (master §6); +SEPAY, +SUSPENSE_HOLD/+SUSPENSE_RESOLVE chỉ là hằng Java + dòng enum ledger.

## 7. Config mới

```yaml
app:
  frontend-url: ${APP_FRONTEND_URL}
  payment:
    booking-hold-minutes: 15
    topup-intent-minutes: 30
    topup-min: 10000
    topup-max: 20000000
  sepay:
    api-key: ${SEPAY_API_KEY}
    bank-bin: ${SEPAY_BANK_BIN}          # build QR
    bank-account: ${SEPAY_BANK_ACCOUNT}
  money:
    payout-min-amount: 50000
  security:
    bank-enc-key: ${BANK_ENC_KEY}        # AES-256-GCM, base64 32 bytes
spring.security.oauth2.client.registration.google:
  client-id: ${GOOGLE_CLIENT_ID}
  client-secret: ${GOOGLE_CLIENT_SECRET}
```

## 8. Security notes riêng P2

Constant-time compare API key · không log payload thô/số TK (mask trừ last4) · encrypt bank account at-rest · webhook 200-after-persist · one-time code UPDATE có điều kiện chống race. (Login-fail counter + khóa 15' ĐÃ KÉO VỀ P1 — master §15 T6, review 2026-07-02 — phase này chỉ verify còn chạy.)

## 9. Test plan (bảng tối thiểu, theo RULE money CI MySQL thật)

| Nhóm | Ca |
|---|---|
| Webhook | sai Apikey → 401 không lưu · đúp sepay_id → 200 không đúp ledger · transferType=out → ghi nhận, không ledger · UNMATCHED → SUSPENSE đúng cặp bút toán · SUM=0 mọi ca |
| Topup-first | TOPUP đúng X thật nhận · BOOKING đủ tiền → CONFIRMED + escrow HELD · trả thiếu → chỉ topup + notify · trả thừa → thừa nằm ví · intent hết hạn tiền vẫn về ví · race webhook vs hold-expiry (lock booking) |
| RESERVED/slot (§2.9) | tạo QR → `claimed_slots++` ngay · hold expiry nhả slot + intent EXPIRED · user hủy → nhả ngay + intent CANCELLED · quét-nhả tại chỗ khi full · re-book reuse row CANCELLED (Đ-P2.6) · pay-lại bằng ví trên RESERVED không đụng slot · gọi mua đúp khi RESERVED → trả intent cũ |
| Payout | đủ/thiếu số dư · REJECT → REVERSAL cân · hủy bởi user · PAID không sinh bút toán · admin-only 403 |
| OAuth | 6 ca matrix: có oauth id / email verified / email chưa verified / user mới / SUSPENDED / code hết hạn+dùng lại |
| Reminder | UNIQUE chặn đúp · app chết qua mốc vẫn gửi 1 lần |
| Reconcile | thiếu bên mình → xử lý như webhook · thiếu bên Sepay → alert |

## 10. Điều kiện DONE của phase

Demo được: nạp ví bằng CK thật (test 1.000đ) → mua vé event paid → escrow release sau 3 ngày → host request payout → admin đánh PAID. Email nhận đủ: biên nhận nạp, xác nhận vé, nhắc T-24/T-1, hóa đơn payout. Login Google chạy trên prod. Reconcile chạy 3 đêm liền không lệch.

## 11. Refresh checklist đầu phase (BẮT BUỘC — doc viết trước ~1 tháng)

- [ ] Đối chiếu docs Sepay hiện hành: tên field webhook, header auth, API list transactions, giới hạn rate.
- [ ] Chốt Đ-P2.1 → Đ-P2.5 với chủ dự án.
- [ ] Kiểm money-core đã merge: tên method LedgerService thật, số migration V thật (V5 có thể trượt).
- [ ] Verify refresh-token rotation đã có chưa (master §5 ⚠).
- [ ] Google Cloud Console: tạo OAuth client (user ops) — redirect URI prod + dev.
- [ ] Sepay dashboard: cấu hình webhook URL + API key (user ops).
- [ ] Backup + restore drill đã chạy (gate tiền thật — master §10).
