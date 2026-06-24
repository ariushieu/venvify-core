# Plan: Thiết kế ERD & Entity — Online Event Platform

**Ngày tạo:** 2026-06-24 · **Trạng thái:** ⏳ CHỜ DUYỆT · **Người duyệt:** chủ dự án
**Liên quan:** `SPEC.md` §5.5 (quy ước ID), §5.6 (phần tiền), §6 (entity), §7 (NFR)

> ⚠️ Đây là **bản thiết kế để duyệt**, CHƯA sinh code. Sau khi bạn duyệt (và chốt các "Decision cần bạn xác nhận" ở cuối), mình mới tạo `@Entity`.
> Mục tiêu: chốt schema **trước** khi viết entity, vì sửa schema sau khi đã có code + data là tốn nhất.

---

## 0. Tiền đề & quy ước chung

### 0.1 Dependency còn thiếu trong `pom.xml` (cần thêm trước khi code)
| Dependency | Dùng cho | Bắt buộc |
|---|---|---|
| `spring-boot-starter-validation` | Bean Validation trên DTO | Có |
| `spring-boot-starter-security` | Auth/JWT | Có (giai đoạn auth) |
| `io.jsonwebtoken:jjwt` (hoặc nimbus) | Phát/verify JWT | Có (giai đoạn auth) |
| `org.mapstruct:mapstruct` + processor | Map DTO ↔ Entity | Có |
| `spring-boot-starter-data-redis` | State ephemeral, cache | Có (giai đoạn realtime/cache) |

> Mình KHÔNG sửa `pom.xml` ở bước này — chỉ liệt kê để bạn nắm. Sẽ thêm khi vào đúng giai đoạn.

### 0.2 Quy ước áp dụng cho MỌI bảng (SPEC §5.5)
- `id` — `BIGINT AUTO_INCREMENT PRIMARY KEY` — khóa nội bộ, dùng cho FK/join, **KHÔNG expose**.
- `public_id` — `CHAR(36)` UUID, `UNIQUE NOT NULL` — ID công khai cho API/FE.
- Mọi FK trỏ tới cột `id` (BIGINT) nội bộ, không dùng `public_id` cho quan hệ.

### 0.3 Base class (giảm lặp) — BẮT BUỘC mọi entity kế thừa

**`BaseEntity`** (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`) — mọi entity kế thừa:

| Trường | Cột DB | Kiểu | Annotation | Ghi chú |
|---|---|---|---|---|
| `id` | `id` | `Long` / BIGINT | `@Id @GeneratedValue(IDENTITY)` | khóa nội bộ, KHÔNG expose |
| `publicId` | `public_id` | `String` / CHAR(36) | `@Column(unique, updatable=false)` | UUID, sinh ở `@PrePersist`; ID công khai |
| `createdAt` | `created_at` | `Instant` | `@CreatedDate @Column(updatable=false)` | bạn đã nghĩ ra |
| `updatedAt` | `updated_at` | `Instant` | `@LastModifiedDate` | bạn đã nghĩ ra |
| `version` | `version` | `Long` | `@Version` | **thêm** — optimistic locking, chống ghi đè đồng thời (quan trọng cho tiền/slot) |

- `createdAt`/`updatedAt` tự động qua Spring Data JPA Auditing → cần bật `@EnableJpaAuditing` ở config.
- `publicId` sinh bằng `UUID` trong `@PrePersist` (không để DB sinh, để app kiểm soát).

**`SoftDeletableEntity extends BaseEntity`** — chỉ entity cần xóa mềm mới kế thừa cái này:
| Trường | Cột DB | Kiểu | Ghi chú |
|---|---|---|---|
| `isDeleted` | `is_deleted` | `boolean` | `DEFAULT false`; filter ở repository (SPEC/CLAUDE.md §4 Soft Delete) |

> Các bảng đánh dấu *(soft delete: ✔)* ở §2 kế thừa `SoftDeletableEntity`; còn lại kế thừa `BaseEntity`.
> **Không lặp lại** `id`/`public_id`/`created_at`/`updated_at`/`version` trong bảng đặc tả ở §2 — mặc định đã có từ base.

> *Tùy chọn để sau (chưa thêm bây giờ):* `createdBy`/`updatedBy` (ai thao tác) — cần auth context, hữu ích cho audit phần tiền nhưng thêm phức tạp. Bổ sung khi đã có Security nếu thấy cần.

### 0.4 Quy ước kiểu dữ liệu
- **Tiền:** `BIGINT`, đơn vị **VND nguyên (đồng)** — VND không có phần lẻ. **KHÔNG dùng** `float`/`double` cho tiền.
- **Thời gian:** `DATETIME` (UTC), app quy đổi timezone ở tầng hiển thị.
- **Enum:** lưu dạng `VARCHAR` (`@Enumerated(EnumType.STRING)`) — KHÔNG dùng ordinal (tránh vỡ khi thêm giá trị).
- **Text dài:** `TEXT`/`LONGTEXT` cho mô tả, nội dung chat, transcript.

---

## 1. Sơ đồ quan hệ tổng quan

```
                         ┌──────────┐
                         │  users   │──┐ (host)
                         └────┬─────┘  │
            (attendee) ┌──────┤        │ 1─N
                       │      │ 1─1    ▼
                  ┌────▼───┐  │   ┌─────────┐ 1─1 ┌────────┐
                  │ wallets│  │   │ events  │────▶│ rooms  │
                  └───┬────┘  │   └────┬────┘     └───┬────┘
                 1─N  │       │   1─N  │              │ 1─N
                  ┌───▼─────┐ │   ┌────▼─────┐   ┌────┴──────────────┐
                  │ ledger_ │ │   │ bookings │   │ polls / questions │
                  │ entries │ │   └────┬─────┘   │ / chat_messages   │
                  └───┬─────┘ │        │ 1─1     └───────────────────┘
                 N─1  │       │   ┌────▼────────┐
                  ┌───▼───────▼─┐ │ escrow_holds│
                  │ transactions│ └─────────────┘
                  └─────────────┘
   events 1─1 ─▶ recordings, summaries
   users  N─N ─▶ follows (follower → host)
   events 1─N ─▶ reviews ; users 1─N ─▶ notifications
```

---

## 2. Đặc tả từng bảng

### 2.1 `users`  *(soft delete: ✔)*
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| public_id | CHAR(36) | UNIQUE, NOT NULL | |
| email | VARCHAR(255) | UNIQUE, NOT NULL | |
| password_hash | VARCHAR(255) | NULL | NULL nếu chỉ đăng nhập OAuth |
| full_name | VARCHAR(150) | NOT NULL | |
| avatar_url | VARCHAR(500) | NULL | |
| bio | TEXT | NULL | |
| host_handle | VARCHAR(60) | UNIQUE, NULL | **vanity URL cho storefront của host**. Tên định danh ngắn do host tự chọn (giống @username), tạo link đẹp `/h/{handle}` thay cho UUID. NULL khi user chưa làm host; chỉ đặt khi bật storefront. UNIQUE để không trùng. |
| oauth_provider | VARCHAR(20) | NULL | GOOGLE… |
| oauth_provider_id | VARCHAR(100) | NULL | |
| email_verified | BOOLEAN | DEFAULT false | |
| status | VARCHAR(20) | NOT NULL | ACTIVE / SUSPENDED |
| created_at, updated_at, is_deleted | | | base |

- **Roles:** xem **Decision D1** (single enum vs multi-role).
- Index: `UNIQUE(email)`, `UNIQUE(public_id)`, `UNIQUE(host_handle)`, `INDEX(oauth_provider, oauth_provider_id)`.

### 2.2 `events`  *(soft delete: ✔)*
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| host_id | BIGINT | FK→users.id, NOT NULL | |
| title | VARCHAR(200) | NOT NULL | |
| slug | VARCHAR(220) | UNIQUE, NOT NULL | SEO URL |
| description | TEXT | NULL | |
| category | VARCHAR(50) | NULL | |
| start_time | DATETIME | NOT NULL | |
| end_time | DATETIME | NOT NULL | |
| max_slots | INT | NOT NULL | |
| claimed_slots | INT | NOT NULL, DEFAULT 0 | counter — xem **D4** |
| price_amount | BIGINT | NOT NULL, DEFAULT 0 | 0 = free (VND) |
| status | VARCHAR(20) | NOT NULL | DRAFT/PUBLISHED/LIVE/ENDED/CANCELLED/POSTPONED |
| cover_image_url | VARCHAR(500) | NULL | |
| created_at, updated_at, is_deleted | | | base |

- Index: `UNIQUE(slug)`, `INDEX(host_id)`, `INDEX(status)`, `INDEX(start_time)`, `INDEX(category)`.

### 2.3 `bookings`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| event_id | BIGINT | FK→events.id, NOT NULL | |
| attendee_id | BIGINT | FK→users.id, NOT NULL | |
| status | VARCHAR(20) | NOT NULL | RESERVED/CONFIRMED/CANCELLED/REFUNDED/ATTENDED/NO_SHOW |
| price_paid | BIGINT | NOT NULL, DEFAULT 0 | snapshot giá lúc đặt |
| purchase_txn_id | BIGINT | FK→transactions.id, NULL | NULL nếu event free |
| booked_at | DATETIME | NOT NULL | |
| created_at, updated_at | | | base |

- **Ràng buộc khóa:** `UNIQUE(event_id, attendee_id)` — 1 người 1 vé/event.
- Index: `INDEX(attendee_id)`, `INDEX(event_id)`.
- Đây là bảng dùng cho internal API "user X có vé hợp lệ cho event Y?" (SPEC §5.2) → truy bằng `(event_id, attendee_id, status IN (CONFIRMED,ATTENDED))`.

### 2.4 `wallets`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| user_id | BIGINT | FK→users.id, UNIQUE, NOT NULL | 1 ví/user |
| currency | VARCHAR(3) | NOT NULL, DEFAULT 'VND' | |
| balance_cached | BIGINT | NOT NULL, DEFAULT 0 | xem **D2** |
| created_at, updated_at | | | base |

### 2.5 `ledger_entries`  *(APPEND-ONLY — không update/delete)*
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| wallet_id | BIGINT | FK→wallets.id, NOT NULL | |
| transaction_id | BIGINT | FK→transactions.id, NOT NULL | |
| amount | BIGINT | NOT NULL | có dấu: + credit / − debit |
| balance_after | BIGINT | NOT NULL | số dư sau bút toán (sao kê/audit) |
| description | VARCHAR(255) | NULL | |
| created_at | DATETIME | NOT NULL | **không** có updated_at |

- **RULE:** Bảng bất biến. Code KHÔNG được `UPDATE`/`DELETE`. Điều chỉnh sai sót = thêm bút toán đảo (reversal).
- Số dư thật = `SUM(amount)` theo `wallet_id`. `wallets.balance_cached` chỉ là cache.
- Index: `INDEX(wallet_id, created_at)`, `INDEX(transaction_id)`.

### 2.6 `transactions`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| type | VARCHAR(20) | NOT NULL | TOPUP/TICKET_PURCHASE/REFUND/PAYOUT/COMMISSION |
| status | VARCHAR(20) | NOT NULL | PENDING/SUCCESS/FAILED/CANCELLED |
| amount | BIGINT | NOT NULL | VND |
| transaction_ref | VARCHAR(100) | UNIQUE, NOT NULL | **idempotency key** |
| payment_provider | VARCHAR(20) | NULL | VNPAY/MOMO/INTERNAL |
| provider_txn_id | VARCHAR(100) | NULL | mã giao dịch phía cổng |
| user_id | BIGINT | FK→users.id, NOT NULL | chủ giao dịch |
| event_id | BIGINT | FK→events.id, NULL | nếu liên quan event |
| created_at, updated_at | | | base |

- **RULE (idempotency):** `UNIQUE(transaction_ref)`. Callback VNPay/MoMo lặp lại với cùng ref → bỏ qua, không tạo bút toán mới.
- Index: `UNIQUE(transaction_ref)`, `INDEX(type)`, `INDEX(status)`, `INDEX(user_id)`.

### 2.7 `escrow_holds`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| event_id | BIGINT | FK→events.id, NOT NULL | |
| booking_id | BIGINT | FK→bookings.id, NOT NULL | xem **D3** (per-booking) |
| gross_amount | BIGINT | NOT NULL | tiền vé gốc |
| commission_amount | BIGINT | NOT NULL | phí platform |
| host_net_amount | BIGINT | NOT NULL | phần host nhận |
| status | VARCHAR(20) | NOT NULL | HELD/RELEASED/REFUNDED/PAID_OUT |
| held_at | DATETIME | NOT NULL | |
| released_at | DATETIME | NULL | |
| created_at, updated_at | | | base |

- Index: `INDEX(event_id)`, `INDEX(booking_id)`, `INDEX(status)`.
- State machine: `HELD → RELEASED → PAID_OUT`, nhánh `HELD → REFUNDED`.

### 2.8 `rooms`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base — `public_id` = room ID Node dùng |
| event_id | BIGINT | FK→events.id, UNIQUE, NOT NULL | 1-1 |
| status | VARCHAR(20) | NOT NULL | WAITING/LIVE/ENDED |
| started_at | DATETIME | NULL | |
| ended_at | DATETIME | NULL | |
| recording_id | BIGINT | FK→recordings.id, NULL | |
| created_at, updated_at | | | base |

### 2.9 `polls`  +  `poll_options`  +  `poll_votes`
**polls**: id, public_id, `room_id` FK, `question` TEXT, `status`(OPEN/CLOSED), `created_by` FK→users, `created_at`, `closed_at`. Index `INDEX(room_id)`.
**poll_options**: id, public_id, `poll_id` FK, `option_text` VARCHAR(255), `vote_count` INT DEFAULT 0, `display_order` INT.
**poll_votes**: id, `poll_id` FK, `poll_option_id` FK, `user_id` FK, `created_at`. **`UNIQUE(poll_id, user_id)`** (1 vote/poll). Index `INDEX(poll_option_id)`.

> Live voting đi qua Socket.IO + Redis (ephemeral). Kết quả được persist xuống các bảng này (SPEC §5.3, §5.6 lưu MySQL). `vote_count` là counter denormalized.

### 2.10 `questions`  +  `question_upvotes`  (Q&A)
**questions**: id, public_id, `room_id` FK, `asker_id` FK→users, `content` TEXT, `upvote_count` INT DEFAULT 0, `status`(PENDING/ANSWERED/DISMISSED), `answered_at` NULL, base. Index `INDEX(room_id, status)`, `INDEX(room_id, upvote_count)`.
**question_upvotes**: id, `question_id` FK, `user_id` FK, `created_at`. **`UNIQUE(question_id, user_id)`** (1 upvote/người).

### 2.11 `chat_messages`
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| room_id | BIGINT | FK→rooms.id, NOT NULL | |
| sender_id | BIGINT | FK→users.id, NOT NULL | |
| content | TEXT | NOT NULL | |
| created_at | DATETIME | NOT NULL | không updated_at |

- Index: `INDEX(room_id, created_at)`.
- ⚠ Ghi nhiều (write-heavy). MVP: lưu thẳng MySQL. Ghi chú tương lai: cân nhắc retention/archive nếu lớn.

### 2.12 `recordings`
id, public_id, `event_id` FK UNIQUE, `storage_url` VARCHAR(500), `file_size_bytes` BIGINT, `duration_seconds` INT, `status`(PROCESSING/READY/FAILED), base.

### 2.13 `summaries`  (AI)
id, public_id, `event_id` FK UNIQUE, `transcript_url` VARCHAR(500) *(lưu file ở storage, không nhồi DB)*, `summary_content` TEXT, `top_questions` TEXT *(JSON)*, `status`(PENDING/READY/FAILED), `model_used` VARCHAR(50), base.

### 2.14 `follows`
id, `follower_id` FK→users, `host_id` FK→users, `created_at`. **`UNIQUE(follower_id, host_id)`**. Index `INDEX(host_id)`.

### 2.15 `reviews`
id, public_id, `event_id` FK, `reviewer_id` FK→users, `host_id` FK→users *(denormalized để tính rating tổng của host)*, `rating` TINYINT (1–5), `comment` TEXT, base. **`UNIQUE(event_id, reviewer_id)`**. Index `INDEX(host_id)`, `INDEX(event_id)`.

### 2.16 `notifications`
id, public_id, `user_id` FK, `type` VARCHAR(40), `title` VARCHAR(200), `content` TEXT, `is_read` BOOLEAN DEFAULT false, `related_entity_type` VARCHAR(30) NULL, `related_entity_public_id` CHAR(36) NULL, `created_at`. Index `INDEX(user_id, is_read)`, `INDEX(user_id, created_at)`.

---

## 3. Tổng hợp Enum
| Enum | Giá trị |
|---|---|
| UserStatus | ACTIVE, SUSPENDED |
| Role (xem D1) | ATTENDEE, HOST, ADMIN |
| EventStatus | DRAFT, PUBLISHED, LIVE, ENDED, CANCELLED, POSTPONED |
| BookingStatus | RESERVED, CONFIRMED, CANCELLED, REFUNDED, ATTENDED, NO_SHOW |
| TransactionType | TOPUP, TICKET_PURCHASE, REFUND, PAYOUT, COMMISSION |
| TransactionStatus | PENDING, SUCCESS, FAILED, CANCELLED |
| PaymentProvider | VNPAY, MOMO, INTERNAL |
| EscrowStatus | HELD, RELEASED, REFUNDED, PAID_OUT |
| RoomStatus | WAITING, LIVE, ENDED |
| PollStatus | OPEN, CLOSED |
| QuestionStatus | PENDING, ANSWERED, DISMISSED |
| RecordingStatus / SummaryStatus | PROCESSING/PENDING, READY, FAILED |

---

## 4. Decision cần bạn xác nhận (duyệt kỹ phần này)

| # | Vấn đề | Khuyến nghị | Lý do |
|---|---|---|---|
| **D1** | User roles: 1 enum `role` vs nhiều role (bảng `user_roles`) | **Multi-role** (Set) | Spec cho phép "bất kỳ ai" vừa host vừa attend → một user cần cả 2 vai trò. ADMIN tách riêng. |
| **D2** | Số dư ví: thuần derived (`SUM(ledger)`) vs có cột `balance_cached` | **balance_cached + ledger** | Đọc số dư nhanh; cache chỉ cập nhật trong CÙNG transaction với insert ledger; có job reconcile định kỳ so `balance_cached == SUM(amount)`. Vẫn giữ ledger là nguồn sự thật. |
| **D3** | Escrow: per-booking vs per-event | **Per-booking** | Refund/release từng vé chính xác; xử lý được ca 1 attendee hủy trong khi event vẫn chạy. |
| **D4** | Đếm slot: counter `claimed_slots` + lock vs `COUNT(bookings)` mỗi lần | **Counter + `SELECT ... FOR UPDATE` trên row event** | Tránh oversell khi nhiều người claim đồng thời; nhanh hơn count. |
| **D5** | Lưu chat/poll/Q&A ở MySQL (history) | **Đồng ý** theo SPEC | Live state ở Redis; persist xuống MySQL. Không dùng Mongo. |
| **D6** | Đơn vị tiền | **BIGINT VND nguyên** | VND không có phần lẻ; tránh sai số float. |
| **D7** | Kiểu `public_id` | **UUID v7 (CHAR(36))** | Có tính thứ tự thời gian (index tốt hơn UUID v4). Nếu muốn ngắn hơn cho URL → cân nhắc ULID/base62 — báo mình nếu bạn thích hướng này. |

---

## 5. Phạm vi & các bước sau khi duyệt

Sau khi bạn duyệt + chốt D1–D7, mình sẽ làm theo **vertical slice** (CLAUDE.md §7), KHÔNG sinh hết 19 bảng một lượt. Thứ tự đề xuất:

1. **Base layer:** `BaseEntity`, `SoftDeletableEntity`, bật JPA Auditing, các enum.
2. **Slice 1 — User** (entity + repository) — nền cho auth.
3. **Slice 2 — Event + Booking.**
4. **Slice 3 — Wallet + LedgerEntry + Transaction + EscrowHold** (phần tiền — làm cẩn thận, có test).
5. **Slice 4 — Room + Poll/Question/Chat.**
6. **Slice 5 — Recording + Summary + Follow + Review + Notification.**

> Mỗi slice xong mình báo file đã tạo; bạn xác nhận rồi mới sang slice kế.

### Lưu ý ngoài phạm vi plan này (chốt riêng sau)
- Thêm dependency vào `pom.xml` (§0.1) — làm khi vào đúng giai đoạn.
- **Migration: `DECISION` (cập nhật 2026-06-24) → dùng Flyway** (versioned migration), `ddl-auto: validate`. Chuyển sớm lúc chưa có data nên gần như không rủi ro. V1 sinh từ Hibernate export (profile `schema-gen`), từ V2 trở đi mỗi lần đổi entity viết thêm 1 file `V*.sql`. (Trước đó định để `ddl-auto: update` rồi Flyway sau bảo vệ — đã quyết chuyển luôn.)
- `application.yaml` cấu hình datasource qua biến môi trường (CLAUDE.md §4).

---

## 6. Checklist duyệt (cho bạn tick khi review)
- [ ] BaseEntity đủ trường (id, public_id, created_at, updated_at, version) — đồng ý thêm `version` & `public_id`
- [ ] Migration để `ddl-auto: update` (chốt Flyway sau khi bảo vệ nếu startup)
- [ ] Danh sách 19 bảng đủ, không thiếu thực thể nào so với SPEC §6
- [ ] D1 — roles
- [ ] D2 — balance_cached
- [ ] D3 — escrow per-booking
- [ ] D4 — slot counter + lock
- [ ] D5 — chat/poll ở MySQL
- [ ] D6 — tiền BIGINT VND
- [ ] D7 — public_id UUID v7
- [ ] Các ràng buộc UNIQUE (booking, vote, upvote, review, follow) hợp lý
- [ ] Index đủ cho truy vấn nóng (discover event, Q&A theo upvote, ledger theo ví)
- [ ] Đồng ý thứ tự vertical slice ở §5
```
