# Kiến trúc kỹ thuật hệ thống — venvify-core

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT · **Loại:** master (sống lâu, sửa qua changelog §17)
**Phạm vi:** Spring Boot backend + hạ tầng VPS + tích hợp ngoài. **FE KHÔNG thuộc doc này** (chốt 2026-07-02 — chủ dự án tự lo FE; backend chỉ cam kết contract REST/WS + nginx serve static).
**Quan hệ tài liệu:** `SPEC.md` (yêu cầu sản phẩm) · `master/20260702-master-roadmap.md` (phase/scope/cut-line) · `master/20260624-erd-entity-design.md` (data) · `details/*` (thiết kế từng phase — PHẢI bám doc này).

## 0. Cách dùng
- Ký hiệu: ✅ đã có trong code (chuẩn hóa thành RULE) · 📐 chuẩn cho code sắp viết · ⚠ lệch, cần sửa.
- Detail plan không được mâu thuẫn doc này. Buộc phải lệch → sửa doc này trước (ghi changelog), rồi mới code.

---

## 1. Stack chốt theo tầng

| Tầng | Công nghệ | Trạng thái | Ghi chú |
|---|---|---|---|
| Runtime | Java 21 · Spring Boot **4.1.0** (webmvc, data-jpa, security, validation) | ✅ | Boot 4 tách autoconfig theo module — thêm starter phải kiểm artifact đúng dòng 4.x (đã gặp với `spring-boot-flyway`) |
| Logging | Log4j2 (starter logging bị exclude ở MỌI dependency) | ✅ | Dependency mới cũng phải exclude `spring-boot-starter-logging` |
| DB | MySQL 8.0 InnoDB, utf8mb4 · Flyway | ✅ | `ddl-auto: validate`, Flyway sở hữu schema |
| Auth | JWT HS256 (jjwt 0.12.6), access 15' + refresh 7d (DB) | ✅ | `sub` = user public_id, claim `roles[]` |
| OAuth | `spring-boot-starter-oauth2-client` (Google) | 📐 P2 | BE-centric flow, chi tiết plan P2 §4 |
| Email | Resend HTTP API (`EmailService` interface) | ✅ | Reminder/receipt P2 dùng luôn, không thêm SMTP |
| Realtime media | **LiveKit self-host** (SFU + TURN embedded + Egress) | 📐 P4, chờ O-MP1 | PoC 2 tuần đầu P4; fallback = LiveKit Cloud (đổi URL/key, không đổi code) |
| Realtime data | Spring WebSocket **STOMP**, SimpleBroker in-memory | 📐 P4 | Chat/poll/Q&A/raise-hand. 1 instance → không cần Redis relay; scale-path ghi §14 |
| Cache | Caffeine in-process | 📐 P1 (T6) → P3 | Vào từ P1 cho login-fail counter (T6 §15); cache list ở P3. Redis KHÔNG vào Spring; Redis chỉ tồn tại trên VPS2 phục vụ LiveKit egress |
| Storage | Cloudflare R2 qua AWS SDK v2 (S3 client) | 📐 P4/P5 | Bucket private + presigned URL |
| AI | Whisper (OpenAI/Groq — chọn qua interface) + Claude API | 📐 P5 | Provider = interface + config, mock được trong test |
| Docs API | springdoc-openapi 3.0.x (swagger-ui) | ✅ | OpenAPI là contract với FE |
| CI/CD | GitHub Actions: test (MySQL service) → build/push Docker Hub → SSH deploy → healthcheck | ✅ | |
| Ingress | nginx host-level + certbot, rate limit 10r/s/IP | ✅ | |

**RULE (chống phình stack):** không thêm công nghệ ngoài bảng — cụ thể KHÔNG: MongoDB, Kafka/RabbitMQ (queue = bảng DB, §8), Elasticsearch (FULLTEXT đủ), microservice tự viết mới. Muốn thêm → sửa doc này trước.

---

## 2. Module & ranh giới phụ thuộc

Package-by-feature hiện có: `common, user, event, booking, wallet, room, interaction, content, social, notification` (+ `admin` mở ở P6). Tính năng mới xếp vào module theo domain: Sepay/payout → `wallet`, LiveKit/attendance → `room`, AI/recording → `content`, discovery → `event`, transfer → `booking`.

Ma trận phụ thuộc cho phép (hàng gọi cột, qua **service** — KHÔNG import repository chéo module):

| gọi ↓ / bị gọi → | common | user | event | booking | wallet | room | interaction |
|---|---|---|---|---|---|---|---|
| user | ✔ | — | | | | | |
| event | ✔ | ✔ | — | | | | |
| booking | ✔ | ✔ | ✔ | — | ✔ (EscrowService) | | |
| wallet | ✔ | ✔ | ✔ (đọc) | ✔ (đọc) | — | | |
| room | ✔ | ✔ | ✔ | ✔ (kiểm vé) | | — | |
| interaction | ✔ | ✔ | | | | ✔ | — |
| content | ✔ | | ✔ | ✔ | | ✔ | ✔ (top Q&A) |
| social | ✔ | ✔ | ✔ | ✔ (ATTENDED check) | | | |
| notification | ✔ | ✔ | | | | | |
| admin (P6) | gọi mọi module qua service công khai | | | | | | |

- **RULE:** không phụ thuộc vòng. `notification` KHÔNG được module khác gọi trực tiếp để "gửi" — nó **nghe domain event**.
- **Domain events nội bộ** (Spring `ApplicationEventPublisher`): side-effect (email, notification in-app, enqueue AI) chạy ở `@TransactionalEventListener(phase = AFTER_COMMIT)`. **RULE:** transaction chứa tiền/booking KHÔNG chứa side-effect ngoài DB.
- Catalog event khởi điểm: `BookingConfirmedEvent`, `EventPublishedEvent`, `EventCancelledEvent`, `EventPostponedEvent`, `PayoutPaidEvent`, `RecordingReadyEvent`, `SummaryReadyEvent` — khai báo tại module phát, thêm dần theo phase.

---

## 3. Topology runtime (prod)

```
                     internet
                        │ :443 TLS (certbot)
              ┌─────────▼──────────┐  VPS1 (đang có)
              │ nginx               │ rate-limit, sec headers, WS upgrade
              │  /            → FE static (ngoài phạm vi)
              │  /api/v1/*    → 127.0.0.1:8080
              │  /api/v1/ws   → như trên + Upgrade/Connection, timeout 3600s
              ├── venvify-core (Docker, 512m, loopback 8080)
              └── MySQL 8 (Docker, loopback 3306, /opt/data)

              ┌────────────────────┐  VPS2 (thuê đầu P4 — O-MP2)
              │ livekit-server      │ :443 udp+tcp (TURN embedded), WS API
              │ livekit-egress      │ → upload R2
              │ redis               │ loopback (psrpc cho egress)
              └────────────────────┘

  Ngoài:  R2 (recording/transcript/avatar) · Resend (email)
          Sepay (webhook → VPS1) · OpenAI|Groq + Anthropic (P5)
```

- **Ports:** VPS1 mở 80/443; VPS2 mở cổng LiveKit theo docs version lúc PoC (dự kiến 443/udp+tcp TURN, 7880-7881); app/DB/Redis loopback-only ✅.
- **DNS:** `api.<domain>` → VPS1 · `live.<domain>` (+ `turn.` nếu tách) → VPS2.
- Spring → LiveKit: gọi server API qua HTTPS + webhook chiều ngược lại (ký JWT). Hai VPS không cần private network — mọi trao đổi đã authenticated.

---

## 4. API conventions (✅ chuẩn hóa từ code hiện có)

- REST dưới context `/api/v1`, resource số nhiều. Định danh public: `public_id` (UUIDv7) hoặc `slug`/`handle`. **RULE:** không bao giờ expose `id` BIGINT.
- Envelope: `ApiResponse{code, message, data, timestamp}` · list: `PagedResponse` · lỗi validation: `FieldValidationError[]` — tất cả qua `GlobalExceptionHandler` ✅.
- Mã lỗi: 400 (nghiệp vụ từ chối), 401 (generic — kể cả SUSPENDED, theo quyết định account-lifecycle), 403, 404, 409 (xung đột trạng thái/race/optimistic), 429 (nginx), 500. **RULE:** message không lộ nội bộ (SQL, stack, id nội bộ); message tiếng Anh (FE dịch).
- Pagination: `?page=0&size=20` (size max 100), sort mặc định `id DESC` (R15 — không sort theo `created_at`).
- Time: ISO-8601 **UTC** (sau T1 §15). Giờ treo tường hiển thị = việc của client theo `events.timezone`.
- **RULE OpenAPI:** endpoint mới phải hiện đúng trên swagger + có unit test (memory rule) thì mới tính xong.
- Webhook nhận từ ngoài đặt dưới `/webhooks/{provider}` — permitAll ở filter nhưng tự xác thực (§5), idempotent tầng DB.

---

## 5. Security architecture

**AuthN**
- JWT HS256 shared-secret (SPEC §5.2 — service khác verify cùng key nếu cần), access 15' stateless (filter dựng Authentication từ claims, không chạm DB) ✅; refresh 7d lưu DB ✅ — ⚠ verify khi vào P2: refresh phải **rotate** (phát cặp mới + revoke cũ); nếu code hiện tại chưa rotate thì bổ sung trong slice OAuth.
- Email verification bắt buộc trước login ✅. OAuth Google (P2): link theo email **chỉ khi** tài khoản local đã `email_verified` — chống account-takeover (chi tiết plan P2 §4).
- Ban: `SUSPENDED` → 401 generic + revoke toàn bộ refresh token (P6 admin).

**AuthZ** — 2 lớp bắt buộc:
1. Route-level: `@PreAuthorize("hasRole('ADMIN')")` cho `/admin/**`; các route còn lại theo SecurityConfig matcher ✅.
2. Ownership check trong service cho MỌI mutation trên resource của người khác (pattern đã dùng ở event/booking ✅ — giữ nguyên).

**Rate limiting**
- nginx zone chung 10r/s/IP ✅; P2 thêm zone riêng cho `/api/v1/auth/(login|register|resend-verification)` ~5r/m (T5 §15); `/webhooks/**` KHÔNG nằm trong zone auth.
- App-level (**P1 — T6 §15**, kéo sớm theo review 2026-07-02: prod không được chạy nhiều tuần với login không chống brute-force): đếm login fail theo account (Caffeine), khóa 15' sau 10 lần sai; response vẫn 401 generic (không lộ trạng thái khóa — khớp chính sách account-lifecycle).

**Secrets** — env-only ✅, không literal password-shaped kể cả dummy CI (bài học GitGuardian). Lộ trình secret theo phase:

| Phase | Secret mới |
|---|---|
| Hiện có | `SECRET_KEY`, `MYSQL_*`, `RESEND_API_KEY`, Docker Hub + SSH (GitHub) |
| P2 | `GOOGLE_CLIENT_ID/SECRET`, `SEPAY_API_KEY`, `BANK_ENC_KEY` (AES-GCM mã hóa số TK host) |
| P4 | `LIVEKIT_API_KEY/SECRET` |
| P5 | `R2_ACCESS_KEY/SECRET/ENDPOINT`, `OPENAI_API_KEY` hoặc `GROQ_API_KEY`, `ANTHROPIC_API_KEY` |

**RULE:** thêm secret = cập nhật `.env.example` + `deploy/README` + GitHub secret nếu CI/CD cần.

**Webhook hardening:** Sepay — verify header Apikey bằng so sánh constant-time; LiveKit — verify JWT chữ ký api-secret. Persist event thô trước, trả 2xx ngay khi đã lưu, xử lý lỗi để job retry (không để provider retry-storm).

**Upload (avatar/cover):** presigned PUT thẳng lên R2, BE không nhận bytes; content-type whitelist; key ngẫu nhiên UUID. (Trước khi có R2: chưa hỗ trợ upload — URL ngoài.)

**OWASP checklist rà ở P7:** injection (JPA param ✅) · IDOR (public_id + ownership ✅) · CSRF (không cookie auth → disable hợp lệ ✅) · XSS (BE không render HTML) · rate limit · secrets · TLS/HSTS (bật sau khi cert ổn định — comment sẵn trong nginx.conf) · security headers ✅ · dependency scan: bật GitHub **Dependabot alerts** (T3 §15, user 1 click).

---

## 6. Data conventions & kỷ luật migration

- `BaseEntity` (id, public_id UUIDv7, created_at, updated_at, `@Version`) ✅; `SoftDeletableEntity` chỉ `users`, `events` ✅. **RULE:** bảng tiền và bảng append-only không bao giờ soft/hard-delete.
- Tiền `BIGINT` VND nguyên ✅ · enum `@Enumerated(STRING)` ✅ · text dài `TEXT`.
- **Enum storage = VARCHAR (quyết 2026-07-02 — THAY policy native ENUM trước đó):** V1 export từ Hibernate ra ENUM MySQL native → mỗi lần thêm giá trị phải `ALTER ... MODIFY` liệt kê đầy đủ, nguồn lỗi lặp (review chéo xác nhận, quên 1 giá trị = sự cố prod). **V4 convert một lần MỌI cột enum → `VARCHAR(30)`** (kiểm kê bằng grep `enum(` trong V1__init.sql; set `hibernate.type.preferred_enum_jdbc_type: VARCHAR` để schema-gen/validate khớp — verify tên property lúc code). Từ đó: thêm giá trị enum = chỉ thêm hằng Java + cập nhật enum ledger, **KHÔNG cần DDL**; bảng mới dùng VARCHAR cho cột enum. Đánh đổi (chấp nhận): mất ràng buộc giá trị tầng DB — `@Enumerated(STRING)` chỉ ghi được giá trị hợp lệ từ code; SQL tay phải cẩn thận.
- **Enum ledger** (mọi thay đổi GIÁ TRỊ enum vẫn ghi vào đây — hết cần DDL nhưng vẫn cần trace; V-number = phase giá trị xuất hiện):

| Enum | Giá trị hiện tại | Kế hoạch thêm |
|---|---|---|
| TransactionType | COMMISSION, PAYOUT, REFUND, TICKET_PURCHASE, TOPUP | +REVERSAL (V4) · +SUSPENSE_HOLD, SUSPENSE_RESOLVE (V5 — đề xuất) · +TICKET_RESALE (V6) |
| PaymentProvider | INTERNAL, MOMO, VNPAY | +SEPAY (V5) |
| BookingStatus | ATTENDED, CANCELLED, CONFIRMED, NO_SHOW, REFUNDED, RESERVED | (chưa có kế hoạch) |
| EscrowStatus | HELD, PAID_OUT, REFUNDED, RELEASED | (chưa) |
| EventStatus | CANCELLED, DRAFT, ENDED, LIVE, POSTPONED, PUBLISHED | (chưa) |

- **⚠ Time policy (T1 §15):** hiện `hibernate.jdbc.time_zone` + `jackson.time-zone` = `Asia/Ho_Chi_Minh` trong khi MySQL container chạy `+00:00` và ERD tuyên bố UTC. Chuẩn hóa **UTC toàn tuyến** (đổi 2 dòng config) ở đầu P1 code, khi DB chưa có data thật — để sau này là một cuộc migrate data đau đớn.
- **Flyway RULE:** file đã applied là bất biến · naming `V{n}__{snake_case}.sql` · mỗi slice một file · prod migrate luôn có backup trước (§10).
- **Migration ledger** (số từ V6 là dự kiến, chốt lúc code — đánh lại 2026-07-03: V4 bị email-OTP chiếm nên money-core dồn xuống V5, các số sau +1):

| V | Nội dung | Trạng thái |
|---|---|---|
| V1 | init schema (Hibernate export) | ✅ đóng băng |
| V2 | event time D13 + wallet account_type + seed 4 hũ hệ thống | ✅ |
| V3 | auth tokens (refresh, email verification) | ✅ |
| V4 | email OTP (đổi verify link → mã 6 số; ngoài kế hoạch ledger cũ) | ✅ |
| V5 | money-core: triggers append-only, CHECKs, **convert mọi cột enum → VARCHAR(30)**, completed_at/refunded_at/paid_out_at (+REVERSAL là hằng Java, sau convert không cần DDL) | ✅ code 2026-07-03 |
| V6 (P2) | payment_intents, sepay_webhook_events, host_bank_accounts, payout_requests, event_reminders, auth_login_codes, bookings.reserved_until (giá trị enum mới: không cần DDL) | 📐 |
| V7 (P3) | ticket_transfers, bookings.transfer_count, FULLTEXT index events | 📐 |
| V8 (P4) | room_attendances, events.recording_enabled, recordings.audio_url, chat moderation cols | 📐 |
| V9 (P5) | ai_jobs | 📐 |
| V10 (P6) | audit_logs, reviews.hidden | 📐 |

- Index policy: FK tự có index (InnoDB); composite cho query nóng khai ở entity ✅; detail plan của query list mới phải nêu index nó dùng; EXPLAIN các query discover trước khi ship P3.

---

## 7. Transaction & concurrency (nâng R13–R18 của money-core thành chuẩn chung)

- 1 use-case = 1 `@Transactional` ở service; controller không mở transaction; query dùng `readOnly = true`.
- **CẤM** `REQUIRES_NEW` lồng trong luồng tiền (R18).
- **Lock ordering toàn cục** (chống deadlock): `Event` row (slot) → `Booking` row → `Wallet` rows theo **id tăng dần** (R13) → sau khi chạm wallet không lock thêm gì khác.
- **RULE:** không gọi external API (Resend/Sepay/LiveKit/AI/R2) bên trong DB transaction. Pattern: commit → `AFTER_COMMIT` listener hoặc job.
- Optimistic `@Version` mặc định cho non-money; `OptimisticLockingFailureException` → 409 cho client retry. Pessimistic (`FOR UPDATE`) CHỈ ở: wallet, event slot, booking lúc confirm/transfer, claim `ai_jobs` (`SKIP LOCKED`).
- Handler webhook/job idempotent theo pattern 3 bước: `SELECT ... FOR UPDATE` → verify state → mutate. Chạy lại không đúp.

---

## 8. Jobs catalog (`@Scheduled`, single-instance — chưa cần ShedLock; nếu chạy >1 instance app thì thêm, ghi nhận sẵn)

| Job | Lịch | Phase | Idempotent nhờ |
|---|---|---|---|
| Escrow release (auto-end + release sau delay 3d) | theo plan money-core §3.4 (mỗi giờ, phút lệch 0/30) | P1 | state check dưới lock |
| Reconcile ledger (4 bất biến) | 03:17 hằng ngày | P1 | chỉ đọc; lệch = P0 → email admin NGAY (§9) |
| Booking hold expiry (nhả slot RESERVED quá hạn) | mỗi 1' | P2 | status check dưới lock |
| Payment intent expiry | mỗi 5' | P2 | status |
| Reminder T-24h / T-1h | mỗi 5' | P2 | `UNIQUE(event_id, kind)` |
| Sepay daily reconcile (đối chiếu API list) | 04:05 | P2 | chỉ so khớp + báo |
| Room quét treo (WAITING/LIVE quá hạn) + finalize attendance | mỗi 5' + webhook-driven | P4 | room status |
| ai_jobs poller | mỗi 30s | P5 | `FOR UPDATE SKIP LOCKED` + attempts |
| Notification purge >90d | 04:41 Chủ nhật | P6 | delete idempotent |
| mysqldump backup (cron VPS, ngoài app) | 02:35 hằng ngày | trước P2 | — |

**RULE:** thêm job = thêm dòng vào bảng này + test idempotency (chạy 2 lần liên tiếp không đúp hiệu ứng). Async side-effect (email/notification): executor riêng `@Async`, retry 3 lần backoff, fail thì log + Sentry — không ném ngược caller.

---

## 9. Observability & vận hành

- **Log:** Log4j2 ✅; thêm requestId vào MDC (filter đọc `X-Request-Id` từ nginx `$request_id`) — T2 §15. **RULE log tiền:** ghi đủ (`transaction_ref`, wallet id, amount) nhưng KHÔNG log số dư người khác, số TK đầy đủ, secret, nội dung webhook thô ở mức INFO.
- **Actuator (P2):** thêm starter; expose health/info/metrics trên `management.server.port=8081` loopback-trong-container (không publish cổng); `/health` custom giữ nguyên cho nginx + CD ✅.
- **Sentry BE free tier (P2):** bắt 500 + job fail. **UptimeRobot** ping `/health` mỗi 5' — làm được ngay (user ops, T4).
- Alert đáng giá nhất: reconcile phát hiện `SUM(ledger) ≠ 0` / `balance_cached` lệch / escrow lệch — **sự cố P0: email admin NGAY qua Resend từ P1** (`app.ops.admin-email`), KHÔNG chỉ log ERROR, không đợi Sentry (review 2026-07-02) · webhook UNMATCHED · job fail liên tiếp ≥3 · 5xx đột biến.
- **Runbook** (bổ sung dần vào `deploy/README.md`): rollback = compose up image tag SHA trước đó · restore DB từ dump (§10) · rotate secret (thứ tự: đổi env → recreate app → revoke cũ).

---

## 10. Backup & DR (gate BẮT BUỘC trước P2 — tiền thật)

- Nightly 02:35 cron VPS: `mysqldump --single-transaction --routines venvify_db | gzip` → `/opt/backup/` + đẩy off-site (R2 khi có; trước đó scp về máy dev mỗi tuần). Giữ 14 bản.
- **Restore drill:** 1 lần bắt buộc trước khi nhận tiền thật + mỗi tháng 1 lần nhanh (import vào container tạm, đếm bảng, so vài số dư).
- **Runbook restore viết SỚM (T7 §15):** các bước restore/rollback cụ thể (lệnh, thứ tự, ai làm gì) vào `deploy/README.md` ngay khi setup cron backup ở P1 — platform giữ tiền thật không được ứng biến lúc sự cố; drill chạy đúng theo runbook để kiểm chứng chính nó.
- CD bổ sung bước **pre-deploy dump** (P2): trước `docker compose up` app mới luôn dump nhanh — migration hỏng thì còn đường lùi.
- RPO 24h / RTO ~1h — chấp nhận cho đồ án, nêu rõ trong báo cáo NFR. Binlog PITR = nâng cấp sau, không làm bây giờ.

---

## 11. Testing strategy

- Hiện trạng ✅: unit service (Mockito), MockMvc slice (CORS/Health), CI chạy **MySQL 8 thật** (service container) + `mvn verify`. **GIỮ MySQL thật, không H2** — tránh lệch dialect/ENUM/FULLTEXT.
- **RULE (memory):** endpoint xong = unit test service đi kèm; logic security/serialization đặc thù → thêm MockMvc slice.
- **Money (CLAUDE.md §5):** integration test bắt buộc: SUM(ledger)=0 mọi ca · idempotency (đúp webhook, đúp transaction_ref) · race 2 thread (mua slot cuối, trừ ví song song) — chạy trên MySQL CI.
- Mock external qua interface: `EmailService` ✅ là pattern mẫu → `SepayClient`, `LiveKitClient`, `TranscriptionProvider`, `SummaryProvider` đều interface + implementation thật/mock (WireMock cho HTTP nếu cần).
- Coverage mục tiêu: đường tiền (wallet + booking paid) ≥90% line; module khác ≥70% best-effort; CI gate = xanh/đỏ, không gate % cứng.

---

## 12. Config & profiles

- Profiles: default = dev localhost · `prod` (compose đặt `SPRING_PROFILES_ACTIVE=prod`) ✅ · `schema-gen` hết vai trò — xóa ở P7 dọn dẹp.
- Namespace `app.<module>.<key>`: `app.money.*` (đã chốt) → tới: `app.sepay.*`, `app.payment.*`, `app.oauth.*`, `app.livekit.*`, `app.storage.*`, `app.ai.*`, `app.ops.*` (admin-email nhận alert P0 — từ P1), `app.frontend-url`.
- **RULE:** giá trị nghiệp vụ đổi được (commission %, TTL, min payout, budget AI) = config, không hardcode. Flag nguy hiểm double-gate flag + profile (mẫu: dev-topup, money-core §3.6).

---

## 13. Capacity & performance targets (đo ở P7, không tối ưu sớm)

- Mục tiêu demo/defense: ~100 user đồng thời API · 1–3 event live song song · 30–50 người/room (PoC P4 đo thật trên VPS 4GB — O-MP2) · replay đi thẳng R2, không qua VPS.
- App 512m/1cpu, MySQL 512m/50 conn (compose hiện tại) — theo dõi qua Actuator; chật thì nâng VPS trước, tối ưu code sau.
- N+1: list endpoint dùng fetch join / `@EntityGraph`; OSIV đã off ✅ nên lazy leak nổ sớm ở dev — đúng ý.
- Chat persist: insert per message chịu được ~50 msg/s — đủ; nếu vượt thì batch (ghi hướng, chưa làm).

---

## 14. Những thứ CỐ TÌNH không làm (đồng bộ cut-line roadmap §6)

Không microservice tự viết · không message broker · không Elasticsearch · không multi-region/HA (1 VPS + backup là mức đồ án — nêu hạn chế trong báo cáo) · không GraphQL/gRPC · không Redis trong Spring khi chưa có bài toán thật (multi-instance).

Scale-path đã ghi sẵn (chỉ mở khi cần): >1 app instance → ShedLock + STOMP Redis relay + Redis cache thay Caffeine; chat >50 msg/s → batch insert.

---

## 15. T-fixes — việc kỹ thuật nhỏ làm sớm, không đợi phase

| # | Việc | Khi nào |
|---|---|---|
| T1 | Time policy → UTC toàn tuyến (`hibernate.jdbc.time_zone`, `jackson.time-zone`; recreate DB dev) | đầu P1 code |
| T2 | RequestId MDC filter + nginx `$request_id` | P1–P2 |
| T3 | Bật Dependabot alerts trên GitHub (user, 1 click) | ngay |
| T4 | UptimeRobot ping `/health` (user ops) | sau deploy prod #1 |
| T5 | nginx: zone rate-limit riêng cho `/auth/*` + block WS upgrade sẵn cho P4 | P2 |
| T6 | Login-fail counter theo account (Caffeine) + khóa 15', response 401 generic | đầu P1 code, cùng T1 (review: không ship prod thiếu chống brute-force) |
| T7 | Runbook backup/restore/rollback trong `deploy/README.md` + chạy drill lần 1 | P1, cùng lúc setup cron backup — trước tiền thật |

---

## 16. Open items ảnh hưởng kiến trúc (tham chiếu roadmap §3.2)

- **O-MP1** LiveKit PoC — quyết định lớn nhất; plan P4 viết theo LiveKit self-host, PoC fail → fallback LiveKit Cloud (ít thay đổi) — phương án "giữ Node service" chính thức bỏ nếu PoC pass.
- **O-MP2** specs VPS2 · **O-MP3** R2 · **O-MP4** provider AI (OpenAI vs Groq cho Whisper) · **O-MP7** lịch bảo vệ (neo toàn timeline).

## 17. Changelog

- 2026-07-02 — tạo doc; thay thế SPEC §5.3–5.4 ở tầng kỹ thuật (SPEC giữ vai trò yêu cầu sản phẩm); FE tách khỏi phạm vi kỹ thuật backend.
- 2026-07-02 (review chéo đợt 1) — **enum → VARCHAR từ V4** (bỏ policy native ENUM, user chốt); reconcile lệch = P0 email admin ngay (§9); T6 brute-force login kéo về P1, T7 runbook restore sớm (§15); đặc tả luồng RESERVED+intent (plan P2 §2.9, Đ-P2.6 reuse row); chính sách refund-sau-resale ghi tường minh (20260630 §3.4); supersede markers vào plan 20260630. RULE mới: plan mới ghi đè plan cũ → PHẢI thêm banner ⛔ vào đúng chỗ bị ghi đè trong plan cũ, cùng commit.
