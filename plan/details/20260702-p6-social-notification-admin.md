# Plan kỹ thuật P6 — Follow, Review, Notification, Admin, Analytics

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT (khung — refresh §9 đầu phase) · **Phase:** P6 (T1–T2/2027)
**Tiền đề:** P4 attendance chạy (điều kiện review) · P2 payout endpoints có sẵn (admin gom vào) · bảng follows/reviews/notifications đã có từ V1.
**Bám:** master doc — đặc biệt §2 (notification chỉ NGHE domain event) và §5 (audit).

---

## 1. Follow

- `PUT /hosts/{handle}/follow` · `DELETE /hosts/{handle}/follow` — idempotent (INSERT bỏ qua duplicate UNIQUE(follower, host) / DELETE no-op); không follow chính mình (400).
- `GET /users/me/following` · `GET /hosts/{handle}` (storefront DTO P3) đắp thêm `followerCount` (COUNT query — chưa denormalize; index idx(host_id) đủ tầm nghìn follower).
- **Phát tán event mới:** listener `EventPublishedEvent` (AFTER_COMMIT) → insert notification `NEW_EVENT_FROM_FOLLOWED_HOST` cho mọi follower (batch insert 1 câu) + email `@Async` từng người (cap: > `app.social.notify-email-max-followers` (500) → chỉ in-app, bỏ email — chống nghẽn; ghi trade-off).

## 2. Review

- `POST /events/{id}/reviews` `{rating 1–5, comment ≤ 2000}` — điều kiện: booking **ATTENDED** (data P4) · event ENDED · trong `app.social.review-window-days` (14) kể từ end · UNIQUE(event, reviewer) (đã có V1).
- `GET /events/{id}/reviews` (public, paged, ẩn `hidden`) · `GET /hosts/{handle}/reviews` + `avgRating` (denormalized? — CHƯA: `AVG()` on demand + cache Caffeine 60s; nghìn review vẫn nhanh nhờ idx(host_id)).
- Moderation: `reviews.hidden BOOLEAN DEFAULT false` (V9) — admin hide/unhide (audit §4). Host reply = cut (roadmap).
- Không sửa/xóa review bởi user MVP (tránh reputation gaming vòng sửa) — chỉ admin hide. Ghi rõ cho FE.

## 3. Notification in-app

- `GET /notifications?unreadOnly=&page=` (id DESC) · `PATCH /notifications/{id}/read` · `POST /notifications/read-all` · `GET /notifications/unread-count` (FE poll 30s — đủ MVP, KHÔNG WebSocket riêng cho việc này).
- Nguồn phát: các listener AFTER_COMMIT đã khai (P2 receipts, P4 recording, P5 summary, P6 follow/review) — P6 gom thành **NotificationService.dispatch(type, user, refType, refPublicId, params)** duy nhất + template title/content theo `NotificationType` (enum đã có 7 loại — loại mới phát sinh: PAYOUT_PAID, SUMMARY_READY, TRANSFER_*… chỉ cần thêm hằng Java + enum ledger, KHÔNG cần DDL — cột VARCHAR từ V4, master §6).
- Purge job > 90 ngày (master §8, 04:41 CN).

## 4. Admin (module `admin/` mới — mọi endpoint `@PreAuthorize("hasRole('ADMIN')")`)

**RULE audit:** mọi mutation admin ghi `audit_logs` (V9: admin_id, action VARCHAR(50), target_type, target_public_id, detail JSON, created_at — append-only, KHÔNG update/delete, không API sửa) **trong cùng transaction** với mutation.

| Nhóm | Endpoints | Ghi chú |
|---|---|---|
| Users | `GET /admin/users?q=&status=` · `PATCH /admin/users/{id}/ban` · `/unban` | ban = SUSPENDED + **revoke toàn bộ refresh tokens** + đá STOMP session (nếu online); user thấy 401 generic (memory account-lifecycle) |
| Events | `GET /admin/events?status=&q=` · `POST /admin/events/{id}/takedown` `{reason}` | takedown upcoming paid = force-cancel → tái dùng nguyên luồng refund money-core §3.3 (chạy dưới quyền admin); notification + email attendees |
| Payouts | (P2 đã có) + filter/search | |
| Suspense | `GET /admin/suspense` (webhook UNMATCHED chưa resolve) · `POST /admin/suspense/{webhookId}/resolve` `{userPublicId}` | ledger SUSPENSE → USER + txn `SUSPENSE_RESOLVE` + đánh dấu webhook event PROCESSED + audit |
| Transactions | `GET /admin/transactions?ref=&user=&type=&from=&to=` | đọc-only, phục vụ CSKH/đối soát |
| AI jobs | `GET /admin/ai-jobs?status=DEAD` · `POST /admin/ai-jobs/{id}/retry` | P5 §5 |
| KPI | `GET /admin/dashboard` | 1 DTO: tổng user/host/event theo status, GMV (SUM txn TICKET_PURCHASE+TICKET_RESALE SUCCESS), commission balance (ví COMMISSION), payout pending, suspense balance, event sắp diễn ra 7d — toàn query trên data sẵn, cache 60s |

Seed admin: migration V9 KHÔNG hardcode — runbook: SQL tay set role ADMIN cho user chỉ định (đã ghi P2 §3.3).

## 5. Host analytics (Should — SPEC)

`GET /users/me/host-stats` (tổng events, attendees, doanh thu released, follower, avgRating) · `GET /events/{id}/stats` (host-only: bookings theo status, attendance rate = ATTENDED/CONFIRMED, doanh thu gross/net, poll participation, số câu Q&A) — thuần SELECT trên data sẵn (bookings, room_attendances, escrow_holds, reviews). Không bảng mới, không pre-aggregate — tầm nghìn dòng query trực tiếp nhanh.

## 6. Thay đổi hệ thống

V9__p6_social_admin.sql: `audit_logs` · `ALTER reviews ADD hidden` · config `app.social.{review-window-days: 14, notify-email-max-followers: 500}`. (NotificationType mới không cần DDL — VARCHAR từ V4, master §6.) Module mới `admin/` (controller + service mỏng gọi service các module — ma trận master §2 dòng admin).

## 7. Test plan

Follow idempotent + self-follow 400 + fan-out notification 1 câu batch + cap email · review: matrix eligibility (ATTENDED/NO_SHOW/CONFIRMED/quá window/đúp) + hidden ẩn khỏi public + avg đúng · notification: dispatch template đúng loại, unread-count, purge idempotent · admin: 403 non-admin toàn bộ · ban revoke token thật (refresh cũ chết) · takedown gọi đúng luồng refund (test xuyên: mua → takedown → tiền về ví + SUM=0) · suspense resolve cân sổ · audit ghi trong cùng tx (mutation fail → không có audit row) · KPI số khớp fixture.

## 8. Điều kiện DONE của phase

Admin panel (API) đủ: ban user đang online → bị đá và không refresh được; takedown event paid → attendees nhận refund + email; suspense case resolve sạch; dashboard KPI khớp số tay. Review + follow chạy public. Đây là điểm chốt "Must: Admin panel quản lý user/event/giao dịch" của SPEC §9.1.

## 9. Refresh checklist đầu phase

- [ ] Kiểm kê NotificationType phát sinh thực tế P2–P5 → cập nhật enum ledger master §6 (không cần DDL).
- [ ] Đối chiếu luồng refund money-core thực tế (đã qua 4 phase — code là chuẩn, plan là tham khảo).
- [ ] Rà cut-line roadmap: storefront/host-reply/analytics mở rộng còn thời gian không (P7 hardening ngay sau).
- [ ] Số migration V thật.
