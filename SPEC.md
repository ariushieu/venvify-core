# Online Event Platform — Product & Technical Spec

> Nền tảng web tổ chức và tham dự sự kiện trực tuyến (workshop, AMA, talk show, demo day) — toàn bộ luồng đăng ký → bán vé → vào phòng WebRTC → tương tác real-time → replay + AI summary diễn ra trên một nền tảng duy nhất.

**Version:** 1.0 · **Scope:** Đồ án tốt nghiệp solo (1 năm), định hướng startup · **Status:** đang thiết kế.

---

## 0. Cách đọc tài liệu này (cho AI agent)

- Đây là **nguồn sự thật** về yêu cầu sản phẩm và kiến trúc. Khi sinh code/đề xuất, bám theo các **DECISION** đã chốt bên dưới — không tự ý đổi stack hay mô hình.
- Quy ước đánh dấu:
  - `DECISION:` quyết định đã chốt, không thương lượng lại trừ khi user yêu cầu.
  - `OPEN:` còn để mở, cần hỏi user trước khi làm.
  - `RULE:` ràng buộc bắt buộc khi implement (đặc biệt phần tiền & bảo mật).
- Ưu tiên tính năng theo **MoSCoW**: `Must` > `Should` > `Nice`.
- Thứ tự build: **làm xong toàn bộ core Spring Boot trước, rồi mới sang realtime Node.js.**

---

## 1. Tổng quan

### 1.1 Vấn đề
- Host phải ghép nhiều tool rời rạc: Eventbrite (đăng ký) + Zoom (họp) + Google Form (phản hồi).
- Không có nền tảng nội địa hỗ trợ luồng VietQR/Sepay tích hợp cho sự kiện trực tuyến.
- Sau sự kiện nội dung thất lạc: không replay, không tóm tắt.
- Attendee thiếu công cụ tương tác có cấu trúc (Q&A, poll, raise hand) — phải dùng tool thứ 3.

### 1.2 Giải pháp
- Một nền tảng: tạo event → bán vé → host room WebRTC → tương tác live → replay + AI summary.
- Nhiều event chạy song song, hoàn toàn độc lập.
- Thanh toán nội địa qua VietQR/Sepay và ví in-app.
- Host có storefront riêng, build audience theo thời gian.

---

## 2. Đối tượng & Mục tiêu

### 2.1 Người dùng
- **Host:** developer/designer/educator chia sẻ kiến thức; startup làm demo day/product launch; freelancer/coach bán workshop.
- **Attendee:** sinh viên, người đi làm muốn học kỹ năng; người dùng general.

### 2.2 Mục tiêu sản phẩm (kèm chỉ số định lượng)

| Mục tiêu | Chỉ số đo lường |
|---|---|
| Host tạo & tổ chức event hoàn toàn trên nền tảng | Không redirect sang tool ngoài |
| Attendee claim slot và vào room nhanh | ≤ 3 click từ trang event đến trong room |
| Nhiều event diễn ra đồng thời không ảnh hưởng nhau | ≥ N room WebRTC độc lập song song |
| Host kiếm doanh thu từ sự kiện | Thanh toán Sepay/VietQR hoạt động end-to-end |
| Nội dung được lưu trữ và tóm tắt | Replay + AI summary sinh ra ≤ 5 phút sau event |

---

## 3. Luồng nghiệp vụ

### 3.1 Host — tạo & tổ chức event
1. Đăng ký, hoàn thiện hồ sơ storefront.
2. Tạo event: tên, mô tả, thời gian bắt đầu/kết thúc, số slot tối đa, giá vé (free/paid).
3. Hệ thống tự tạo trang event public + link đăng ký (SEO-friendly URL).
4. Host sửa/hủy event trước khi diễn ra.
5. Đến giờ: Host vào room — hệ thống tạo WebRTC room độc lập cho event đó.
6. Trong room: mic/cam, quản lý attendee, chạy poll, xử lý Q&A queue, spotlight.
7. Kết thúc: đóng room → lưu recording → AI xử lý summary.
8. Nhận payout sau khi event kết thúc thành công.

### 3.2 Attendee — tham dự event
1. Discover qua trang chủ / tìm kiếm / link trực tiếp.
2. Xem chi tiết event: mô tả, thời gian, slot còn lại, giá vé.
3. Claim slot: đăng nhập → nếu paid thì **trừ ví in-app** (nạp ví trước qua Sepay/VietQR nếu chưa đủ số dư) → nhận email xác nhận.
4. Nhận reminder trước event (email/notification).
5. Vào room đúng giờ: click link → WebRTC room mở trên trình duyệt.
6. Trong room: chat sidebar, raise hand, vote poll, submit/upvote Q&A.
7. Sau event: xem replay, đọc AI summary, để lại review.

### 3.3 Thanh toán & payout
- Attendee nạp ví in-app qua Sepay/VietQR.
- Mua vé → **trừ ví** (ghi nhận giao dịch ngay vào ledger).
- Platform giữ tiền trong **escrow** cho đến khi event kết thúc.
- Sau event: platform thu **commission (vd 10%)**, phần còn lại chuyển vào ví Host.
- Host rút tiền (payout) — xem `RULE` ở §6.4 về xử lý payout MVP.

> `RULE:` Mọi luồng tiền phải nhất quán với mô hình ví. Không có nhánh "thanh toán trực tiếp vé" tách rời khỏi ví.

---

## 4. Đặc tả tính năng (MoSCoW)

### Auth & Profile
| Tính năng | Ưu tiên |
|---|---|
| Đăng ký / đăng nhập (email + OAuth Google) | Must |
| Phân quyền Host / Attendee | Must |
| Hồ sơ cá nhân: avatar, bio, social links | Must |
| Host storefront: danh sách event, follower count, review tổng hợp | Must |
| Follow host — nhận thông báo (email + in-app) khi có event mới | Should |

### Event Management
| Tính năng | Ưu tiên |
|---|---|
| Tạo/sửa/xóa event với đầy đủ thông tin | Must |
| Cài đặt số slot tối đa | Must |
| Event free hoặc paid (đặt giá vé) | Must |
| Trang event public với SEO-friendly URL | Must |
| Host hủy/dời event — tự động email cho attendee (hoàn tiền vào ví nếu hủy) | Must |
| Tìm kiếm và lọc event theo danh mục, thời gian | Should |
| Trang discover: nổi bật, sắp diễn ra, theo danh mục | Should |

### Slot & Ticket
| Tính năng | Ưu tiên |
|---|---|
| Attendee claim slot (tạo booking) | Must |
| Thanh toán qua ví in-app | Must |
| Nạp ví qua Sepay / VietQR | Must |
| Email xác nhận booking | Must |
| Lịch sử booking của Attendee | Should |

### WebRTC Room
| Tính năng | Ưu tiên |
|---|---|
| Tạo room tự động khi Host bắt đầu event | Must |
| Video/audio call nhiều người (WebRTC) | Must |
| Chat sidebar real-time trong room | Must |
| N phòng chạy song song độc lập | Must |
| Host: mute/unmute attendee, kick, spotlight | Must |
| Attendee: raise hand, bật/tắt mic/cam | Must |
| Màn hình chờ trước khi host bắt đầu | Should |

### Interactive Tools
| Tính năng | Ưu tiên |
|---|---|
| Poll: host tạo, attendee vote real-time, hiển thị kết quả | Must |
| Q&A queue: submit câu hỏi, upvote, host chọn trả lời | Must |
| Reaction emoji real-time | Nice |

### Recording & AI
| Tính năng | Ưu tiên |
|---|---|
| Ghi lại nội dung phòng (audio/video) | Should |
| Replay xem sau sự kiện | Should |
| AI tóm tắt nội dung chính + Q&A hay nhất (từ transcript, giới hạn token) | Should |

### Monetization
| Tính năng | Ưu tiên |
|---|---|
| Ví in-app cho cả Host và Attendee | Must |
| Platform thu commission theo % | Must |
| Host rút tiền (payout) | Must |
| Dashboard doanh thu cho Host | Should |

### Analytics
| Tính năng | Ưu tiên |
|---|---|
| Admin dashboard: quản lý user, event, giao dịch | Must |
| Host xem: số attendee, tỉ lệ tham dự, doanh thu, follower | Should |
| Attendee để lại review + rating cho event/host | Should |

### Notification (email + in-app)
| Tính năng | Ưu tiên |
|---|---|
| Email: xác nhận booking, hóa đơn thanh toán | Must |
| Email: nhắc lịch trước event (1 ngày, 1 giờ) | Must |
| Email: event bị hủy, dời lịch, cập nhật nội dung | Must |
| Email + in-app: host mà attendee đang follow tạo event mới | Should |

---

## 5. Kiến trúc & Tech Stack

### 5.1 Tổng quan 3 tầng
- **Frontend (React + TailwindCSS)** — SPA, giao tiếp qua REST API + WebSocket.
- **Backend chính (Spring Boot)** — `DECISION:` **nguồn sự thật duy nhất**. Sở hữu toàn bộ dữ liệu & logic: auth, event, booking, payment, analytics.
- **Real-time Service (Node.js)** — `DECISION:` **relay realtime thuần**. WebRTC signaling + Socket.IO cho chat/poll/Q&A. KHÔNG sở hữu nghiệp vụ; mọi quyết định tiền/quyền đều hỏi Spring.

> `DECISION:` Thứ tự build = Spring core xong hết → rồi mới Node realtime.

### 5.2 Cơ chế tin cậy giữa 2 backend
- `RULE:` Auth dùng **JWT stateless** ngay từ Spring (KHÔNG session/cookie thuần — tránh phải đập lại khi sang Node).
- Spring ký JWT bằng key nó giữ; Node verify bằng cùng public key → biết user là ai, không cần gọi qua lại mỗi request.
- Khi user vào room: Node gọi **internal API của Spring** `"user X có vé hợp lệ cho event Y không?"` để authorize.
- `RULE:` Không ai vào được room trả phí nếu chưa thanh toán. Chỉ một nguồn sự thật (Spring).

### 5.3 Tech Stack

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Backend chính | Spring Boot 4.1, Java 21, Spring Security (JWT), Spring Data JPA | Nghiệp vụ core |
| Real-time Service | Node.js, Socket.IO, WebRTC (SFU — `OPEN`, chốt sau) | Signaling, chat, poll, Q&A |
| TURN/STUN | coturn (hoặc TURN của giải pháp SFU) | `RULE:` Bắt buộc — peer sau NAT/firewall |
| Database | **MySQL (InnoDB)** | `DECISION:` Toàn bộ nghiệp vụ + lịch sử chat/poll/Q&A. (Không dùng MongoDB) |
| Cache / Realtime state | Redis | Session, state ephemeral trong room (raise hand, poll đang mở), rate limiting |
| Payment | Sepay webhook + VietQR | Nạp ví, đối soát tiền vào bank platform |
| AI Summary | OpenAI Whisper + GPT API | Transcribe → tóm tắt (từ transcript, giới hạn token) |
| Storage | AWS S3 / DigitalOcean Spaces | Recording, avatar, thumbnail |
| DevOps | Docker, GitHub Actions, Nginx | CI/CD, reverse proxy, SSL |
| Deployment | DigitalOcean VPS | Self-hosted |

### 5.4 Luồng WebRTC
- Frontend kết nối Socket.IO tới Node sau khi đã được Spring authorize (JWT + kiểm tra vé).
- Node = signaling server: trao đổi SDP offer/answer, ICE candidates.
- `DECISION (mô hình):` sự kiện là **broadcast 1 host – N người nghe** → hướng **SFU** (server fan-out 1 stream host tới N người) hợp hơn mesh P2P.
- `OPEN:` giải pháp SFU cụ thể — **LiveKit (self-host)** vs **tự xây mediasoup** — chốt ở đầu giai đoạn realtime.
- `RULE:` TURN bắt buộc (~20-30% user sau NAT không kết nối nếu chỉ STUN). Test bằng 4G, không chỉ localhost.
- Chat/poll/Q&A/raise hand đi qua Socket.IO events — tách khỏi WebRTC stream.
- Mỗi event = một room ID độc lập → N event song song = N socket namespace riêng.

### 5.5 Quy ước ID cho bảng dữ liệu
- `RULE:` Mỗi bảng có **2 cột ID**:
  - **`id` BIGINT auto-increment** — khóa chính nội bộ, chỉ dùng cho join/quan hệ trong DB. KHÔNG expose ra ngoài.
  - **`public_id` VARCHAR (UUIDv7)** — ID công khai, dùng cho API/FE.

### 5.6 Thiết kế phần tiền (Wallet & Payment)
`RULE:` Tiền không được sai một xu. Bốn nguyên tắc bắt buộc:

1. **Ledger append-only** — KHÔNG lưu số dư bằng cột `balance` cộng/trừ. Dùng bảng bút toán chỉ-thêm (mỗi dòng = một entry có dấu +/-); số dư = tổng các dòng. Audit được, không mất tiền do race.
2. **Idempotency cho webhook** — webhook Sepay có thể lặp. Mỗi giao dịch có `transaction_ref` duy nhất; lần lặp phải bị bỏ qua, không cộng ví 2 lần.
3. **Khóa khi trừ tiền** — dùng transaction DB + `SELECT ... FOR UPDATE` (InnoDB) khi trừ ví, tránh hai request đồng thời trừ trên số dư cũ.
4. **Máy trạng thái escrow** — `held → released → paid_out`, nhánh `refunded`. Xử lý đủ ca: host hủy (refund attendee), event lỗi kỹ thuật (refund/credit), attendee không tới (vẫn tính tiền host).

> `RULE (payout MVP):` Sepay là bank watcher/webhook, không phải disbursement API. MVP: hệ thống tính đúng số tiền + đánh dấu trạng thái, admin chuyển khoản tay rồi xác nhận "đã chi". Tự động hóa sau khi có pháp nhân/provider payout phù hợp.

---

## 6. Mô hình dữ liệu (entity chính)

> Mọi entity tuân theo quy ước 2 cột ID ở §5.5. Đây là danh sách khung — agent có thể đề xuất cột chi tiết khi implement.

| Entity | Vai trò | Quan hệ chính |
|---|---|---|
| `User` | Tài khoản (Host hoặc Attendee), profile, OAuth | 1-N Event (host), 1-1 Wallet |
| `Event` | Sự kiện: thời gian, slot, giá, trạng thái | N-1 User(host), 1-N Booking |
| `Booking` | Vé/đăng ký của attendee cho 1 event | N-1 User, N-1 Event, 1-1 Transaction(mua vé) |
| `Wallet` | Ví của user (số dư = tổng ledger) | 1-N LedgerEntry |
| `LedgerEntry` | Bút toán append-only (+/-) | N-1 Wallet, N-1 Transaction |
| `Transaction` | Giao dịch nạp/mua/refund/payout, `transaction_ref` duy nhất | 1-N LedgerEntry |
| `EscrowHold` | Tiền giữ cho 1 event, state machine | N-1 Event |
| `Room` | Phòng WebRTC của 1 event, room ID độc lập | 1-1 Event |
| `Poll` / `PollVote` | Poll trong room + phiếu vote | N-1 Room |
| `Question` (Q&A) | Câu hỏi, upvote count, trạng thái trả lời | N-1 Room, N-1 User |
| `ChatMessage` | Lịch sử chat trong room | N-1 Room, N-1 User |
| `Recording` | File ghi + metadata, link replay | 1-1 Event |
| `Summary` | AI summary (transcript + tóm tắt) | 1-1 Event |
| `Follow` | Quan hệ attendee → host | N-N qua bảng nối |
| `Review` | Rating + nhận xét cho event/host | N-1 Event, N-1 User |
| `Notification` | Thông báo in-app | N-1 User |

> `OPEN:` ERD chi tiết (kiểu cột, index, FK) — sinh khi bắt đầu code Spring.

---

## 7. Yêu cầu phi chức năng (NFR)

- **Bảo mật:** mật khẩu hash (BCrypt), JWT có hạn + refresh token, kiểm tra quyền ở mọi endpoint, chống truy cập room trái phép (§5.2). Validate input, chống SQL injection (dùng JPA/prepared statement).
- **Tiền:** xem §5.6 — ledger, idempotency, locking, escrow state machine là bắt buộc.
- **Hiệu năng:** độ trễ video chấp nhận được cho broadcast; chịu được `OPEN` (số người/room, số room song song — định lượng khi chốt SFU).
- **Khả dụng:** xử lý mất kết nối trong room (reconnect), retry email/notification.
- **Khả mở rộng:** Node realtime tách khỏi Spring để scale riêng phần room.

---

## 8. Kế hoạch phát triển (12 tháng, solo)

| Giai đoạn | Timeline | Nội dung |
|---|---|---|
| 1 | Tháng 1–2 | Thiết kế hệ thống, ERD, API contract. Auth (JWT), quản lý user, CRUD event. CI/CD, deploy skeleton lên VPS. |
| 2 | Tháng 3–4 | Slot booking, trang event public, Sepay/VietQR, **ví in-app + ledger**, email notification. |
| 3 | Tháng 5–6 | WebRTC room (Node.js), chat real-time, poll, Q&A queue, raise hand, spotlight. |
| 4 | Tháng 7–8 | Recording, AI summary (Whisper + GPT), replay, host storefront, follow system. |
| 5 | Tháng 9–10 | Analytics dashboard, payout flow, admin panel, review system. |
| 6 | Tháng 11–12 | Performance tuning, security audit, UI polish, báo cáo, chuẩn bị bảo vệ. |

---

## 9. Tiêu chí nghiệm thu

### 9.1 Must (bắt buộc pass)
- Đăng ký/đăng nhập/phân quyền Host-Attendee đúng.
- Host tạo/sửa/hủy event thành công.
- Attendee claim slot, trừ ví, nhận email xác nhận.
- Host mở room WebRTC — attendee vào được, video/audio ổn định.
- Chat real-time hoạt động.
- Poll + Q&A queue real-time.
- Nhiều event song song không xung đột.
- Payout: tiền vào ví Host sau khi event kết thúc.
- Admin panel quản lý user/event/giao dịch.

### 9.2 Should (điểm cộng)
- AI summary đúng & hữu ích.
- Replay xem lại được.
- Host storefront đủ thông tin (history, follower, review).
- Analytics dashboard đúng số liệu.
- Email reminder đúng giờ.

### 9.3 Luồng demo end-to-end (10 phút)
1. Host tạo event trả phí.
2. Attendee claim slot + thanh toán.
3. Cả hai vào room — video, chat, poll.
4. Host kết thúc — AI summary hiển thị.
5. Host xem dashboard doanh thu.

---

## 10. Open decisions (cần user chốt)

| # | Vấn đề | Trạng thái |
|---|---|---|
| 1 | Giải pháp SFU: LiveKit self-host vs mediasoup tự xây | `OPEN` — chốt đầu giai đoạn 3 |
| 2 | Giới hạn định lượng: người/room, room song song tối đa | `OPEN` — chốt khi có SFU |
| 3 | ERD chi tiết (cột, index, FK) | `OPEN` — sinh khi bắt đầu giai đoạn 1 |
