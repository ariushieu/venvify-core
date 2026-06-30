# Plan: Slice Event + Booking (CRUD + đặt vé event FREE)

**Ngày tạo:** 2026-06-30 · **Trạng thái:** ⏳ CHỜ DUYỆT · **Người duyệt:** chủ dự án
**Liên quan:** [`20260630-ticket-transfer-and-payment.md`](./20260630-ticket-transfer-and-payment.md) §3.1/§7, ERD D4 (slot counter), D13 (event time), CLAUDE.md §3–§8

> ⚠️ Bản thiết kế để duyệt, CHƯA sinh code. Duyệt + chốt §6 `OPEN` xong mình mới code.

---

## 0. Phạm vi (đã chốt với chủ dự án)

- **Trong slice:** CRUD + vòng đời **Event**, và **Booking cho event FREE** (`price = 0`) — đặt/huỷ vé, đếm slot có khoá chống oversell (D4). Thuần Java, **không cần migration** (entity đã khớp DB sau V2).
- **NGOÀI slice (để slice kế):** mua vé **trả phí qua ví** (double-entry `WalletService`), top-up, Sepay, transfer. Booking event trả phí sẽ bị chặn với message rõ ràng ở slice này.
- **Lý do tách:** dựng khung Event + Booking + slot-lock trước, không nửa vời phần tiền. Khi `WalletService` xong, nhánh trả phí ghép vào đúng chỗ.

---

## 1. Quyết định

- **E1 — Quyền tạo event:** user đã đăng nhập nào cũng tạo được; **lần đầu tạo → tự thêm role `HOST`**. Cổng kiểm soát đặt ở payout (slice sau), không ở tạo event. *(OPEN-able, xem §6.)*
- **E2 — Slug:** sinh từ `title` (slugify) + đảm bảo duy nhất (đụng thì thêm hậu tố ngắn). `slug` set lúc tạo, không cho sửa (ổn định URL).
- **E3 — Time (D13):** `start_time`/`end_time`/`timezone` **cho NULL khi DRAFT**; **bắt buộc khi PUBLISH** (service enforce). → sửa `CreateEventRequest` bỏ `@NotNull/@Future` ở time, thêm `timezone`.
- **E4 — Booking event FREE:** tạo thẳng `CONFIRMED`, `price_paid = 0`, `claimed_slots++` trong cùng transaction có khoá row event (D4). Event trả phí → `BadRequestException` "Paid tickets require wallet payment (coming next)".

---

## 2. Event — endpoints (`/api/v1`)

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/events` | Bearer | Tạo event **DRAFT** (auto-grant HOST). Trả `EventResponse` 201. |
| PUT | `/events/{publicId}` | Bearer (owner) | Sửa. DRAFT: sửa mọi field. PUBLISHED: chỉ field không phá vỡ (title/description/category/cover); `maxSlots` chỉ được **tăng** & ≥ `claimedSlots`; **giá khoá** sau publish. |
| PATCH | `/events/{publicId}/publish` | Bearer (owner) | DRAFT→PUBLISHED. Enforce: `startTime`, `endTime`, `timezone` ≠ null; `startTime` tương lai; `endTime > startTime`; `maxSlots ≥ 1`. |
| PATCH | `/events/{publicId}/cancel` | Bearer (owner) | →CANCELLED. (Event FREE: chỉ đổi status; refund tiền = slice sau khi có escrow.) |
| GET | `/events` | Public | List **PUBLISHED**, paginated, filter `category` (optional), sort `startTime`. |
| GET | `/events/{publicId}` | Public* | Detail. DRAFT chỉ owner xem được (*nếu là owner). |
| GET | `/events/mine` | Bearer | Event của chính host (mọi status), paginated. |
| DELETE | `/events/{publicId}` | Bearer (owner) | Soft delete (`is_deleted=true`), chỉ khi DRAFT/CANCELLED & không có booking active. |

- Reschedule/POSTPONE (set `original_start_time`) — **optional**, có thể thêm `PATCH /events/{id}/reschedule` ở cuối slice nếu còn thời gian; không thì để slice sau.
- LIVE/ENDED do vòng đời room điều khiển (slice room sau), không nằm ở đây.

## 3. Booking — endpoints (`/api/v1`)

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/bookings` | Bearer | Đặt vé `{eventPublicId}`. Chỉ event **PUBLISHED**. FREE → CONFIRMED ngay. Paid → 400 (chặn, slice sau). |
| GET | `/bookings/mine` | Bearer | Vé của tôi, paginated. |
| GET | `/bookings/{publicId}` | Bearer | Detail (attendee sở hữu **hoặc** host của event). |
| PATCH | `/bookings/{publicId}/cancel` | Bearer (attendee) | Huỷ vé trước khi event bắt đầu → CANCELLED, `claimed_slots--`. (FREE không động tiền.) |

### 3.1 Luồng `POST /bookings` (FREE) — chống oversell (D4)
1. Resolve user hiện tại (`publicId` từ SecurityContext) + event theo `eventPublicId`, phải `PUBLISHED`.
2. Chặn trùng: `findByEventIdAndAttendeeId` đã tồn tại → `ConflictException` "Already booked".
3. **`eventRepository.findByIdForUpdate(eventId)`** (PESSIMISTIC_WRITE) → kiểm `claimedSlots < maxSlots`, hết chỗ → `BadRequestException` "Sold out".
4. Nếu `priceAmount > 0` → `BadRequestException` (chặn paid, slice sau).
5. Tạo `Booking` CONFIRMED (`pricePaid=0`, `bookedAt=now`), `event.claimedSlots++`. 1 transaction → commit. Khoá row đảm bảo 2 người đặt chỗ cuối không cùng qua.

> `@Transactional` ở service. Lock row event là điểm cốt lõi chống bán quá số chỗ.

## 4. Thay đổi DTO / file

**Sửa:**
- `event/dto/CreateEventRequest` — bỏ `@NotNull/@Future` trên `startTime`/`endTime` (DRAFT cho NULL); thêm `timezone` (optional, `@Size(max=40)`).
- `event/dto/EventResponse` — thêm `timezone` (và `originalStartTime` nếu làm reschedule).
- `event/mapper/EventMapper` — map thêm `timezone`.

**Tạo mới:**
```
common/util/SlugGenerator.java          # slugify + đảm bảo unique (inject EventRepository ở service)
event/service/EventService.java
event/controller/EventController.java
booking/service/BookingService.java
booking/controller/BookingController.java
```

- Không entity mới, **không migration** (Event/Booking columns đã đủ).
- Ownership check: so `event.host.id` với user hiện tại (resolve `findByPublicId`). Sai chủ → `ForbiddenException`.
- Auto-grant HOST: khi tạo event, nếu user chưa có `Role.HOST` thì add vào `roles` + save (token cũ chưa có HOST tới lần login/refresh kế — chấp nhận, vì tạo event không cần claim HOST trong token).

## 5. RULE (đưa vào service)
- **B1:** Chỉ owner sửa/publish/cancel/xoá event (`ForbiddenException` nếu không).
- **B2:** Publish phải đủ `startTime`(tương lai)/`endTime`(>start)/`timezone`; thiếu → `BadRequestException` nêu rõ field.
- **B3:** Đặt vé chỉ khi event PUBLISHED; chống oversell bằng row-lock event (D4); chống đặt trùng bằng unique (event, attendee).
- **B4:** Huỷ vé chỉ trước khi event bắt đầu (`now < startTime`); trả slot.
- **B5:** Sửa event PUBLISHED không được giảm `maxSlots` dưới `claimedSlots`, không đổi giá.
- **B6:** Mọi GET collection đều `Pageable` (CLAUDE.md §4).

## 6. OPEN — cần bạn chốt
- **EO1 — Quyền tạo event (E1):** auto-grant HOST cho mọi user khi tạo event? (Khuyến nghị: **có**, gate ở payout.) Hay bắt buộc nâng cấp HOST trước?
- **EO2 — Đặt vé event FREE đã đủ cho slice này?** Hay bạn muốn gộp luôn nhánh **trả phí qua ví** (kéo theo `WalletService` double-entry + top-up tạm) vào slice này luôn?
- **EO3 — Reschedule/POSTPONE** (set `original_start_time`): làm trong slice này hay để sau?

## 7. Sau khi duyệt
Code 1 lượt: Event (service+controller+slug util+DTO) → Booking (service+controller). Không migration. Xong báo file đã đổi; bạn run thử qua Swagger (tạo→publish→đặt vé FREE→huỷ) rồi commit.
