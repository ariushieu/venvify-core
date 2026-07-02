# Plan kỹ thuật P4 — Realtime: LiveKit (video) + Spring WebSocket (chat/poll/Q&A)

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT (khung — PHẢI refresh §12: doc viết trước phase ~2 tháng, LiveKit docs/version sẽ trôi) · **Phase:** P4 (T9–T11/2026) — **phase rủi ro nhất, không bao giờ cắt (roadmap §6)**
**Tiền đề:** P2 chạy prod · VPS2 đã thuê (O-MP2, user ops) · R2 bucket sẵn (O-MP3 — egress upload thẳng R2).
**Quyết định treo:** O-MP1 — doc viết theo **LiveKit self-host**; PoC §1 quyết. Fallback = LiveKit Cloud (đổi URL/key, giữ nguyên code Spring). Phương án Node/mediasoup bị loại nếu PoC pass.

---

## 0. Kiến trúc tổng (thay SPEC §5.4 ở tầng kỹ thuật)

```
FE (LiveKit React SDK)◄──WebRTC/TURN──► VPS2: livekit-server ──psrpc/redis── egress ──► R2
   │                                          │  ▲
   │ REST (token, control)                    │  │ server API (create room, mute, kick…)
   ▼                                 webhooks ▼  │
VPS1: venvify-core (Spring) ◄─────────────────┘  │
   ▲                                             │
   └──STOMP /api/v1/ws──► chat / poll / Q&A / raise-hand / spotlight (persist MySQL)
```

- **LiveKit CHỈ lo media** (audio/video/screenshare/TURN/recording). **Mọi realtime-data đi STOMP** của Spring — một đường duy nhất để persist + moderation + authz, không split-brain. (Đây là kiến trúc thay cho Node service — SPEC §5.1 DECISION được amend khi O-MP1 chốt.)
- Room name = `event.public_id`; participant identity = `user.public_id` — mapping 1-1 sang domain, webhook tra ngược được.

---

## 1. PoC 2 tuần (mở màn phase — O-MP1)

**Tuần 1 — infra:** VPS2: cài docker, chạy `livekit-server` (config yaml: keys, TURN embedded + cert, webhook → VPS1) + `livekit-egress` + redis. Test bằng LiveKit Meet example (không code): 2 máy + 1 điện thoại **4G** (TURN — RULE SPEC §5.4), screenshare, rớt mạng 30s reconnect.
**Tuần 2 — spike tích hợp:** Spring mint token (jjwt hoặc SDK `io.livekit:livekit-server`) · nhận webhook verify chữ ký · start/stop egress qua API → file lên R2. FE test = LiveKit React example (không tự design — FE ngoài phạm vi).

**Tiêu chí PASS (đo, ghi số vào doc này khi xong):**

| Tiêu chí | Ngưỡng |
|---|---|
| 1 pub + 30 sub ổn định 30' trên VPS 4GB | CPU < 70%, không drop liên tục |
| Join qua 4G (TURN) | ≤ 5s, không manual config |
| Egress RoomComposite mp4 + audio-only | cả 2 file chạy được, lên R2 |
| Reconnect sau rớt mạng 30s | tự hồi < 10s |
| Effort tích hợp Spring | token+webhook+egress ≤ 5 ngày công |

FAIL bất kỳ dòng nào → LiveKit Cloud (giữ code, đổi endpoint; check pricing tại thời điểm đó) — quyết trong 1 ngày, không kéo dài.

---

## 2. Room lifecycle (gắn EventStatus — không thêm trạng thái mới)

| Bước | Trigger | Hành động |
|---|---|---|
| Host "Start event" | `POST /events/{id}/room/start` (host, PUBLISHED, trong `[start_time − 15', ∞)`) | event → LIVE · tạo/lấy `rooms` row (WAITING → LIVE, `started_at`) · LiveKit CreateRoom (empty_timeout 300s, max_participants = maxSlots + 5) · auto-start recording nếu `events.recording_enabled` (§7) |
| Attendee join | `GET /events/{id}/room/token` | validate §3 → trả `{livekitUrl, token}` |
| Host "End event" | `POST /events/{id}/room/end` | event → ENDED · room → ENDED (`ended_at`) · LiveKit DeleteRoom (đá hết) · stop egress · finalize attendance §6 |
| Room rỗng quá empty_timeout | webhook `room_finished` | room → ENDED + finalize attendance; event GIỮ LIVE (host có thể mở lại phòng) — event ENDED chỉ bởi host hoặc auto-end job (money-core §3.4, theo `end_time`) |
| Quét treo (job 5') | room LIVE mà event ENDED/CANCELLED > 10' | đóng room, finalize — hàng rào khi webhook lạc |

**RULE:** escrow release vẫn CHỈ neo theo `event.status = ENDED` + delay (money-core R11) — room không đụng tiền.

## 3. Token & access control

`GET /events/{id}/room/token` — 1 query duy nhất, trả JWT LiveKit TTL 1h (FE gọi lại khi hết — endpoint là "refresh"):

| Ai | Điều kiện | Grants |
|---|---|---|
| Host | event LIVE (hoặc PUBLISHED trong cửa sổ start − 15' — vào chuẩn bị) | roomAdmin, canPublish, canPublishData, canSubscribe |
| Attendee | booking CONFIRMED/ATTENDED + event LIVE | canSubscribe; canPublish=false (broadcast model — SPEC §5.4); canPublishData=false (data đi STOMP) |
| Promote lên nói (host mời) | §5 raise-hand accept | Spring gọi LiveKit `UpdateParticipant` cấp canPublish runtime — KHÔNG mint token mới |

Không thoả → 403. `SUSPENDED` user đã chết từ 401 filter. **RULE giữ nguyên SPEC §5.2:** không ai vào phòng paid mà chưa có vé — một nguồn sự thật là bảng bookings.

## 4. Webhooks LiveKit → `POST /webhooks/livekit`

Verify: header Authorization = JWT ký bằng api-key/secret (SDK có helper). Idempotency: LiveKit gửi `id` event — bảng dedupe không cần (xử lý idempotent theo state), nhưng log đủ.

| Event | Xử lý |
|---|---|
| `participant_joined` | upsert `room_attendances` (§6): first_joined_at, sessions++ |
| `participant_left` | cộng dồn `total_seconds`, set last_left_at |
| `room_finished` | room ENDED nếu chưa + finalize §6 |
| `egress_ended` | recordings: status READY/FAILED + storage_url/audio_url + size/duration → publish `RecordingReadyEvent` (P5 nghe) |

## 5. STOMP — chat / poll / Q&A / raise-hand / spotlight

**Hạ tầng:** `spring-boot-starter-websocket` · endpoint `/ws` (không SockJS) · SimpleBroker in-memory (1 instance — master §1) · nginx VPS1 thêm Upgrade/Connection + `proxy_read_timeout 3600s` cho `/api/v1/ws` (T5 đã chuẩn bị).

**AuthN/AuthZ:**
- CONNECT: header `Authorization: Bearer <access JWT>` → `ChannelInterceptor` verify (JwtTokenProvider sẵn có), gắn Principal = publicId.
- SUBSCRIBE `/topic/room.{eventPublicId}.*`: interceptor check quyền (host hoặc booking CONFIRMED/ATTENDED) — cache kết quả Caffeine 5' key (user, event) để không query mỗi subscribe.
- SEND `/app/room.{id}.*`: validate lại quyền + **rate limit in-memory 10 msg/10s/user** (vượt → error frame, không disconnect).

**Topics & lệnh (bảng contract cho FE):**

| Client SEND | Server broadcast | Persist |
|---|---|---|
| `/app/room.{id}.chat` `{content ≤ 1000}` | `/topic/room.{id}.chat` `{messageId, sender{publicId,name,avatar}, content, at}` | `chat_messages` insert đồng bộ (trong tx nhỏ riêng, KHÔNG chặn broadcast — ghi xong mới broadcast để có id) |
| host: `/app/room.{id}.chat.delete` `{messageId}` | `.chat` `{type: DELETED, messageId}` | set `deleted_by/deleted_at` (V7 — không hard delete, giữ vết moderation) |
| host: poll create/close (REST `POST /events/{id}/polls`, `POST /polls/{id}/close` — mutation qua REST cho validate/swagger, broadcast qua STOMP) | `.poll` `{poll, options, status}` | `polls`/`poll_options` |
| `/app/poll.{id}.vote` `{optionId}` | `.poll` kết quả tổng (throttle 1 msg/s/poll) | `poll_votes` UNIQUE(poll,user) + `vote_count` UPDATE atomic |
| `/app/room.{id}.question` `{content}` | `.qa` `{question}` | `questions` |
| `/app/question.{id}.upvote` | `.qa` `{questionId, upvotes}` | `question_upvotes` UNIQUE + counter |
| host: `/app/question.{id}.answer` / dismiss | `.qa` `{questionId, status}` | `questions.status/answered_at` |
| `/app/room.{id}.hand` `{raised: bool}` | `.presence` `{userId, raised}` | KHÔNG persist (ephemeral, in-memory map/room, clear khi leave/room end) |
| host: `/app/room.{id}.spotlight` `{userId}` + mute/kick (REST → LiveKit API) | `.presence` `{spotlight: userId}` | không persist |
| (emoji reaction — Nice, cut-line #1) | `.reaction` broadcast thuần | không persist |

Lịch sử khi join: REST `GET /events/{id}/chat?before=&size=50` (id DESC), `GET /events/{id}/polls`, `GET /events/{id}/questions?sort=upvotes` — đã có bảng từ V1.

## 6. Attendance (nền cho review P6 + analytics + NO_SHOW)

Bảng mới **`room_attendances`** (V7): booking_id FK UNIQUE · room_id FK · first_joined_at · last_left_at · session_count INT · total_seconds INT. (Host không có booking — không track.)
Finalize khi room ENDED: booking có attendance `total_seconds > 0` → **ATTENDED**; CONFIRMED còn lại → **NO_SHOW**. Idempotent (chỉ đổi từ CONFIRMED). Đồng thời chốt R-T2 (P3 §1.4): vé ATTENDED không transfer.

## 7. Recording control

- `events.recording_enabled BOOLEAN NOT NULL DEFAULT true` (V7) — host chọn lúc tạo/sửa event.
- Start room → nếu enabled: khởi 2 egress song song: **RoomComposite mp4 720p** (replay) + **audio-only OGG** (P5 transcribe rẻ — không phải tách audio từ mp4 sau). Upload thẳng R2 (egress config S3-compatible endpoint). `recordings` row PROCESSING, `audio_url` cột mới V7.
- Stop khi room end (tự theo room). Egress fail → webhook FAILED → Sentry + notification host "recording failed" (không auto-retry MVP — ghi hạn chế báo cáo).
- Replay (P5 mở cho user; P4 chỉ cần file tồn tại): quyền = host hoặc booking ATTENDED/CONFIRMED.

## 8. Thay đổi hệ thống

- **pom:** `spring-boot-starter-websocket` · `io.livekit:livekit-server` (exclude logging nếu kéo theo — RULE master §1). KHÔNG thêm Redis vào Spring.
- **V7__p4_realtime.sql:** `room_attendances` · `ALTER events ADD recording_enabled` · `ALTER recordings ADD audio_url VARCHAR(500)` · `ALTER chat_messages ADD deleted_by BIGINT NULL, ADD deleted_at DATETIME(6) NULL`.
- **Config:** `app.livekit.{url, api-key: ${LIVEKIT_API_KEY}, api-secret: ${LIVEKIT_API_SECRET}}` · `app.storage.r2.*` (endpoint, bucket, keys — egress dùng trực tiếp, Spring dùng từ P5).
- **VPS2 (docs riêng `deploy/livekit/`):** compose livekit + egress + redis; config yaml theo docs version lúc PoC; cert cho TURN domain; firewall.
- **SecurityConfig:** `/ws` permitAll ở HTTP layer (auth ở CONNECT frame); `/webhooks/livekit` permitAll + tự verify.

## 9. Failure modes

| Sự cố | Hành vi hệ thống |
|---|---|
| VPS2 sập giữa event | FE mất media; STOMP (VPS1) vẫn sống — chat thông báo; token endpoint trả 503 (health-check LiveKit fail); host quyết: chờ/cancel (cancel → refund chuẩn). Platform không tự refund (Đ-P4.1 dưới) |
| Webhook lạc/đến muộn | job quét treo 5' (§2) + attendance dựa cộng dồn — sai số chấp nhận |
| Egress chết giữa chừng | recording FAILED, replay không có — event vẫn hợp lệ, không ảnh hưởng tiền |
| App VPS1 restart giữa event | STOMP client tự reconnect (FE SDK); raise-hand state mất (ephemeral — chấp nhận); LiveKit không ảnh hưởng |

**Đ-P4.1 (chốt đầu phase):** chính sách refund khi lỗi kỹ thuật platform giữa event — đề xuất MVP: host quyết định cancel (refund 100% — luồng money-core §3.3) hay tiếp tục; platform không tự động. Admin có thể force-cancel (P6).

## 10. Test plan

Token: matrix grants theo role/vé/status event (unit) · webhook: chữ ký sai 401, joined/left cộng dồn đúng, room_finished idempotent (bắn 2 lần) · attendance finalize: ATTENDED/NO_SHOW đúng, chạy lại không đổi · STOMP: CONNECT không token bị chặn, SUBSCRIBE không vé bị chặn (integration test với client STOMP thật trên MockMvc/embedded), rate limit nhả error frame · poll: vote đúp bị UNIQUE chặn, kết quả cộng đúng dưới 20 thread (race) · chat delete giữ vết. SFU chất lượng media = checklist PoC thủ công (không auto-test).

## 11. Điều kiện DONE của phase

Demo North Star chạy được ở prod: host start event paid → 3+ attendee vào room 4G/wifi (video + chat + poll + Q&A + raise-hand) → host end → attendance ghi đúng, recording mp4 + ogg nằm trên R2, escrow release sau delay. 2 event live song song không lẫn nhau (2 room + 2 topic set).

## 12. Refresh checklist đầu phase (BẮT BUỘC — viết trước ~2 tháng)

- [ ] Docs LiveKit version hiện hành: config yaml, ports, TURN cert, egress S3 config, webhook format, SDK artifact + version.
- [ ] O-MP1 PoC chạy + điền số đo vào §1; quyết pass/fallback trong 1 ngày.
- [ ] O-MP2 VPS2 đã thuê, specs ghi lại; O-MP3 R2 bucket + keys sẵn.
- [ ] Chốt Đ-P4.1; rà lại throttle/TTL/limits (§3, §5) với thực tế PoC.
- [ ] Số migration V thật; đối chiếu enum/cột đã trôi từ V5-V6.
- [ ] nginx VPS1: block WS upgrade đã vào từ T5 chưa.
