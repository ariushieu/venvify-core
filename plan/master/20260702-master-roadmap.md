# Master Roadmap — Venvify (toàn hệ thống)

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT · **Người duyệt:** chủ dự án
**Liên quan:** `SPEC.md` (nguồn yêu cầu, có đính chính ở §5), [`20260624-erd-entity-design.md`](./20260624-erd-entity-design.md) (ERD toàn cục), các plan chi tiết trong [`../details/`](../details/)

> Tài liệu **tầm hệ thống, sống lâu**: trả lời *làm gì, thứ tự nào, cắt gì, quyết định lớn nào phải chốt khi nào*. KHÔNG chứa thiết kế chi tiết — mỗi slice có plan riêng trong `plan/details/` viết ngay trước khi code (CLAUDE.md §2). Cập nhật file này khi: xong 1 phase, chốt 1 OPEN, hoặc đổi scope.

---

## 0. Hai đích đến & kim chỉ nam

| Đích | Deadline | Tiêu chí |
|---|---|---|
| **Bảo vệ tốt nghiệp** | ~T5–6/2027 (`O-MP7` — xác nhận lịch trường) | Demo end-to-end 10' (SPEC §9.3) chạy mượt trên **production thật** + báo cáo |
| **Startup** | sau bảo vệ | Hệ thống production-grade từ đầu (không làm bản "đồ án" rồi đập đi): tiền thật qua Sepay, deploy VPS, CI/CD, backup, monitoring |

**Kim chỉ nam khi phân vân scope** — luồng demo 10' của SPEC §9.3, mọi thứ phục vụ nó là Must:
1. Host tạo event trả phí → 2. Attendee nạp/thanh toán → 3. Cả hai vào room: video + chat + poll → 4. Kết thúc: AI summary hiện ra → 5. Host xem doanh thu.

---

## 1. Kiến trúc đích (bức tranh cuối)

```
                    ┌─────────────────────────┐
                    │   FE SPA (React+Vite)   │  + landing page (repo riêng /landing)
                    └───────┬──────────┬──────┘
                       REST │          │ WebSocket (chat/poll/Q&A/notify)
                            │          │            + WebRTC (video)
                    ┌───────▼──────────▼──────┐   ┌──────────────────────┐
                    │  venvify-core (Spring)  │   │  Media server (SFU)  │
                    │  NGUỒN SỰ THẬT DUY NHẤT │◀──│  O-MP1: LiveKit      │
                    │  auth·event·booking·    │   │  (webhook → Spring,  │
                    │  tiền·room·social·admin │   │  token do Spring ký) │
                    └──┬────────┬────────┬────┘   └──────────┬───────────┘
                       │        │        │                   │ recording (egress)
                  ┌────▼───┐ ┌──▼───┐ ┌──▼─────────┐  ┌──────▼────────┐
                  │ MySQL  │ │Redis │ │ Sepay ─ bank│  │ Object storage│
                  │(Flyway)│ │ (P4) │ │ webhook     │  │ O-MP3 (P5)    │
                  └────────┘ └──────┘ └────────────┘  └───────────────┘
                  Resend (email) · GitHub Actions CI/CD · VPS + nginx + certbot
```

- **DECISION giữ nguyên từ SPEC:** Spring là nguồn sự thật duy nhất; mọi quyết định tiền/quyền nằm ở Spring; realtime chỉ relay.
- **Điểm sẽ chốt lại (O-MP1):** SPEC v1.0 vẽ "Node.js + Socket.IO + SFU tự chọn". Nếu chọn LiveKit self-host thì LiveKit **thay luôn vai trò signaling** của Node, phần chat/poll/Q&A có thể chạy Spring WebSocket → có thể **không cần service Node riêng**. Chốt bằng PoC đầu P4 (xem §4).

---

## 2. Inventory — trạng thái thật (2026-07-02)

### 2.1 Backend `venvify-core`

| Module | Entity+Repo | Service+API | Test | Trạng thái |
|---|---|---|---|---|
| common (base, security JWT, email, health, exception) | ✅ | ✅ | ✅ | **DONE** |
| user / auth (register, login, refresh rotation, verify email, profile cơ bản) | ✅ | ✅ | ✅ 22 test | **DONE** (thiếu OAuth Google — Must, P2; storefront — P6) |
| event (CRUD, publish, cancel, timezone, category, slug SEO) | ✅ | ✅ | ✅ | **DONE** (POSTPONED flow chưa có — P3) |
| booking (vé FREE, slot lock chống oversell, hủy) | ✅ | ✅ | ✅ | **DONE** (paid → P1) |
| wallet (Wallet, LedgerEntry, Transaction, EscrowHold + 4 hũ hệ thống) | ✅ | ❌ | ❌ | **entity-only** → plan money-core ĐÃ DUYỆT ([details](../details/20260702-wallet-money-core.md)) |
| room | ✅ | ❌ | ❌ | entity-only → P4 |
| interaction (Poll/PollOption/PollVote, Question/Upvote, ChatMessage) | ✅ | ❌ | ❌ | entity-only → P4 |
| content (Recording, Summary) | ✅ | ❌ | ❌ | entity-only → P5 |
| social (Follow, Review) | ✅ | ❌ | ❌ | entity-only → P6 |
| notification | ✅ | ❌ | ❌ | entity-only → email P2, in-app P4 |
| admin (quản trị user/event/txn, xác nhận payout, xử lý SUSPENSE) | — | ❌ | ❌ | **chưa có module** → thin P2, full P6 |

### 2.2 Ngoài backend

| Hạng mục | Trạng thái |
|---|---|
| CI (test + Flyway thật trên MySQL 8) + CD (build → DockerHub → SSH deploy → health gate) | ✅ code xong — **chờ user-side ops** (§8) để chạy thật |
| Dockerfile multi-stage non-root, compose, nginx rate-limit, .env.example | ✅ |
| FE `venvify-fe` | ❌ **scaffold Vite trơn** — chưa có page nào (design cũ bị loại, user sẽ định hướng design) |
| Landing page (`/landing` repo gốc) | có sẵn — ngoài scope roadmap này |
| Node/LiveKit, Redis, TURN, storage, AI, backup tự động, monitoring | ❌ chưa bắt đầu (đúng kế hoạch — vào P4/P5/P7) |

> So với SPEC §8: đang ở **tháng 2/12** mà Phase 1 + nửa Phase 2 của SPEC đã xong → **sớm hơn kế hoạch ~1 tháng**. Lợi thế này dành cho P4 (realtime) — phần rủi ro nhất.

---

## 3. Quyết định xuyên suốt

### 3.1 Đã chốt (không mở lại trừ khi user yêu cầu)
D1–D7 (ERD), D8–D13 (transfer/payment/double-entry/event-time), O-M1..O-M4 (money-core: commission 5%, vé paid không tự hủy, auto-ENDED + delay release 3 ngày, dev-topup tắt ở prod) — xem các plan tương ứng. **Payment provider = Sepay** (D11, thay VNPay/MoMo của SPEC).

### 3.2 Phải chốt theo lịch (mỗi cái có deadline — quá hạn là nghẽn phase)

| # | Quyết định | Khuyến nghị | Chốt khi | Lý do khuyến nghị |
|---|---|---|---|---|
| **O-MP1** | Media: **LiveKit self-host** vs mediasoup tự xây vs cloud (Daily/Agora) — kéo theo: còn cần service Node riêng không, hay Spring WebSocket lo chat/poll/Q&A | **LiveKit self-host + Spring WS, bỏ Node** | PoC 2 tuần **đầu P4** (~T9/2026) | mediasoup = nhiều tháng công cho solo; cloud = tốn phí + demo phụ thuộc bên ngoài; LiveKit cho sẵn SFU + TURN nhúng + recording (egress) + React SDK + token là JWT (Spring tự ký bằng jjwt — khớp mô hình "Spring là nguồn sự thật"), webhook về Spring để track attendance. Bỏ Node = bớt 1 backend phải viết/deploy/bảo trì. Đổi lại mất "kiến trúc microservice" trong báo cáo — nếu hội đồng trọng điểm này thì giữ Node thin relay (PoC sẽ cho số liệu để chọn). |
| **O-MP2** | Hạ tầng media: VPS thứ 2 riêng cho LiveKit hay chung VPS app | **VPS riêng** (region SG/VN, ưu tiên bandwidth) | cùng O-MP1 | Video là bandwidth-bound: 1 host 1.5Mbps × 50 viewer ≈ 75Mbps egress — không được để nghẽn chung với API + MySQL. Demo nhỏ có thể chạy chung, nhưng "chuẩn production" = tách. |
| **O-MP3** | Object storage: Cloudflare R2 vs DO Spaces vs S3 | **R2** | đầu P5 (~T11/2026) | Replay = file lớn xem nhiều lần → R2 **miễn phí egress** là khác biệt tiền thật; S3-compatible nên đổi được. Avatar/cover dùng chung bucket. |
| **O-MP4** | AI: STT (Whisper API vs Deepgram...) + summarize (Claude vs GPT) | **Whisper (STT tiếng Việt ổn) + Claude API (summarize)** | đầu P5 | Chi phí pay-per-use nhỏ; summarize là bài của LLM nào cũng đạt — chọn theo giá/chất lượng lúc đó. |
| **O-MP5** | FE hosting: nginx cùng VPS vs Vercel | nginx cùng VPS (đã có sẵn hạ tầng + pattern vps-setup-kit) | P2 (khi FE có trang đầu) | Một chỗ quản lý SSL/domain; Vercel free cũng ổn nếu muốn tách. |
| **O-MP6** | Cut-line MoSCoW khi cháy deadline | thứ tự hy sinh ở §6 | duyệt cùng roadmap này | Thà chốt trước lúc còn tỉnh táo hơn là cắt trong hoảng loạn tháng cuối. |
| **O-MP7** | Lịch bảo vệ thực tế của trường | — | user xác nhận | Toàn bộ timeline §4 neo vào mốc này. |

---

## 4. Roadmap theo phase

> Mỗi phase: mục tiêu 1 câu + đầu việc + **"cuối phase demo được gì"** (điều kiện sang phase sau). Plan chi tiết viết ở đầu mỗi phase. FE đi kèm từng phase (không dể dồn cuối). Effort: phase làm tuần tự, solo.

| Phase | Thời gian | Mục tiêu | Cuối phase demo được |
|---|---|---|---|
| **P0 — Nền tảng** | T6/2026 | ✅ XONG | auth + event + vé free + CI/CD |
| **P1 — Tiền nội bộ** | T7/2026 | Sổ kép chạy đúng từng xu trong nhà | Nạp (dev), mua vé paid bằng ví, event hủy → tiền về ví, event xong → tiền về host, reconcile xanh. **Prod deploy #1 live** + backup DB chạy |
| **P2 — Tiền thật + vòng ngoài** | T8/2026 | Tiền thật vào hệ thống, user thật dùng được | Quét QR Sepay nạp ví/mua vé trên prod; payout thin-admin; email reminder + booking; OAuth Google; FE: auth→event→mua vé→ví |
| **P3 — Transfer & hoàn thiện core** | T9/2026 | Chuyển nhượng vé + trải nghiệm tìm kiếm | Pass vé (tặng/bán lại có handshake); discover/search; POSTPONED flow; FE tương ứng |
| **P4 — Realtime & Video** ⚠️ | T9–T11/2026 | Rủi ro lớn nhất — vào room thật | **PoC 2 tuần chốt O-MP1/O-MP2 trước**, rồi: room lifecycle gate bằng vé, video host→N viewer (test 4G/TURN), chat + poll + Q&A + raise hand realtime, in-app notification. **Đây là điều kiện demo tốt nghiệp** |
| **P5 — Content & AI** | T11/2026–T1/2027 | Sự kiện để lại giá trị sau khi kết thúc | Recording tự động → replay trên web; transcript + AI summary ≤ 5' sau event; attendance ATTENDED/NO_SHOW từ webhook |
| **P6 — Social, Analytics & Admin** | T1–T2/2027 | Vòng lặp giữ chân + vận hành | Storefront `/h/{handle}` + follow + review; dashboard doanh thu host; admin panel full (user/event/txn/SUSPENSE/payout) |
| **P7 — Hardening** | T3/2027 | Chuẩn production đúng nghĩa | Load test N room song song; security audit checklist; monitoring + alert (reconcile lệch phải réo); restore-drill backup; UI polish |
| **P8 — Bảo vệ** | T4–T5/2027 | Báo cáo + demo | Báo cáo (mỗi phase ≈ 1 chương), slides, demo dry-run ×3, **buffer ~1 tháng** cho trượt giá |

**Ràng buộc thứ tự (dependency thật, không đổi được):**
- P1 → P2: Sepay webhook cần ledger + idempotency có sẵn; và cần **prod URL public** (webhook không bắn vào localhost) → user-side ops §8 phải xong trong P1.
- P2 → payout: payout cần tiền thật trong ví host → cần Sepay. Cần thêm bảng `host bank account` (thiếu trong ERD — bổ sung ở plan chi tiết payout).
- P4 độc lập với P2/P3 về code, nhưng **không kéo lên sớm hơn** vì cần FE core (P2) làm nền cho room UI, và cần dư tháng lợi thế hiện tại làm đệm.
- P5 phụ thuộc P4 (recording từ media server). AI chỉ phụ thuộc recording.
- P6 độc lập, đặt sau vì toàn CRUD ít rủi ro — làm lúc não cần nghỉ sau P4/P5.

---

## 5. Đính chính SPEC v1.0 (SPEC vẫn là nguồn yêu cầu; mục này ghi các điểm đã lỗi thời)

| SPEC nói | Thực tế đã chốt |
|---|---|
| Payment VNPay/MoMo | **Sepay** (bank watcher + webhook; không giữ tiền, không disbursement) — D11 |
| §3.3 "mọi luồng tiền qua ví, không có nhánh thanh toán trực tiếp" | Nới theo D11: cho phép **QR trả thẳng vào escrow** (BANK_CLEARING → ESCROW) — vẫn 100% qua ledger, chỉ không ép nạp ví trước. Tinh thần RULE (nhất quán sổ kép) giữ nguyên |
| Commission "vd 10%" | **5%** (O-M1, config) |
| Spring Boot 3.x | **4.1** (Java 21, JUnit 6) |
| Node.js + Socket.IO là DECISION | Hạ xuống **OPEN (O-MP1)** — LiveKit có thể nuốt vai trò signaling; chốt bằng PoC đầu P4 |
| Escrow release khi event xong | + **delay 3 ngày** dispute window (O-M3) |
| §8 timeline 6 giai đoạn | Thay bằng §4 roadmap này (đang sớm hơn ~1 tháng) |

---

## 6. Cut-line khi cháy deadline (O-MP6 — duyệt trước, cắt theo thứ tự, không thương lượng lúc hoảng)

**Không bao giờ cắt (chết demo / chết uy tín):** tiền (P1–P2), video + chat trong room (P4), admin thin (xác nhận payout), backup DB, bảo mật cơ bản.

Thứ tự hy sinh khi trễ (cắt từ trên xuống):
1. Reaction emoji (SPEC đã đánh Nice) — cắt không tiếc.
2. Analytics dashboard host → rút còn 3 con số (attendee, doanh thu, follower).
3. AI summary → rút còn transcript + summary chạy sẵn cho event demo (pipeline thật để post-defense).
4. Ticket transfer (P3) → dời post-defense (plan đã có, không mất công thiết kế lại).
5. Storefront + follow + review → rút còn trang profile host tối thiểu.
6. Recording/replay → rút còn recording thô có link tải (không có player đẹp).

> Realtime (P4) **không nằm trong danh sách cắt** — nó là linh hồn demo. Nếu P4 vỡ tiến độ thì cắt các mục 1–6 để dồn thời gian, không cắt P4.

---

## 7. Top rủi ro & đối sách

| # | Rủi ro | Xác suất/Thiệt hại | Đối sách |
|---|---|---|---|
| R1 | **WebRTC khó hơn dự kiến** (NAT/TURN, chất lượng mạng VN, N room song song) | Cao / Chết demo | PoC 2 tuần TRƯỚC khi commit kiến trúc (O-MP1); test 4G thật từ ngày đầu; LiveKit thay vì tự xây; lợi thế 1 tháng dồn hết cho P4; mốc thoát hiểm: PoC fail → cloud SFU (Daily/LiveKit Cloud) chấp nhận tốn phí |
| R2 | **Bug tiền** (sai một xu, race, webhook lặp) | Trung bình / Chết uy tín + pháp lý | Kỷ luật đã cài sẵn: sổ kép + append-only 3 tầng + idempotency + lock thứ tự + reconcile job + unit test per-API; tiền thật chỉ vào sau khi reconcile chạy xanh ở P1 |
| R3 | **Solo burnout / bus factor = 1** | Trung bình / Trễ dây chuyền | Buffer 1 tháng P8; P6 (CRUD nhẹ) đặt ngay sau 2 phase nặng nhất làm nhịp nghỉ; mọi thứ có plan + test để nghỉ 2 tuần quay lại không mất trí nhớ |
| R4 | **Chi phí hạ tầng vượt túi sinh viên** (VPS media, AI API, storage) | Thấp / Teo scope | Ước tính: VPS app ~$6–12 + VPS media ~$20–40 (chỉ từ P4) + R2 ~$0 + AI ~$5–20/tháng dev. Đã cắt phí bằng lựa chọn (R2 free egress, self-host thay cloud SFU). Theo dõi trong §8 |
| R5 | **Viết báo cáo dồn cục tháng cuối** | Cao / Điểm kém dù code tốt | Mỗi phase xong → chốt 1 chương nháp ngay (plan/master + details chính là nguyên liệu); P8 chỉ còn ghép + trau chuốt |

---

## 8. Việc CHỈ user làm được (gate từng phase — làm sớm không hại)

| Việc | Gate cho | Hạn |
|---|---|---|
| 5 GitHub secrets (DOCKERHUB_USERNAME/TOKEN, VPS_HOST/USERNAME/SSH_KEY) + one-time VPS setup theo `deploy/README.md` + DNS/certbot | Prod deploy #1 (P1) | trong T7/2026 |
| Resolve GitGuardian incident (false positive, commit 62ece18) | vệ sinh dashboard | tiện thì làm |
| Đăng ký **Sepay** + liên kết tài khoản ngân hàng nhận tiền | P2 | đầu T8/2026 |
| VPS thứ 2 cho media (nếu chốt O-MP2 = riêng) | P4 PoC | đầu T9/2026 |
| Tài khoản Cloudflare R2 (hoặc storage đã chốt O-MP3) | P5 | T11/2026 |
| API key OpenAI (Whisper) + Anthropic (Claude) | P5 | T11/2026 |
| Xác nhận lịch bảo vệ (O-MP7) | neo toàn timeline | càng sớm càng tốt |

---

## 9. Sợi chỉ production xuyên suốt (không phải phase riêng — là điều kiện "xong" của mỗi phase)

- **Mỗi slice:** unit test đủ + CI xanh + migration Flyway + không secret trong repo + commit per-feature.
- **P1 thêm:** cron backup MySQL hằng đêm ra ngoài VPS (offsite) — **bắt buộc trước khi có tiền thật**.
- **P2 thêm:** rate-limit tầng app cho auth/webhook (nginx đã có tầng ngoài); log giao dịch đủ truy vết.
- **P4 thêm:** graceful reconnect trong room; kill-switch đóng room từ admin.
- **P7 gom:** monitoring/alert (health, reconcile lệch, disk, cert hết hạn), restore drill có biên bản, security checklist (OWASP top 10 rà tay), load test.

---

## 10. OPEN của master plan

Toàn bộ nằm ở bảng §3.2 (O-MP1..O-MP7) — mỗi cái đã có khuyến nghị + deadline chốt. Duyệt roadmap này = đồng ý **khung phase §4 + cut-line §6**; các O-MP chốt dần đúng lịch, không cần chốt hôm nay.

---

## 11. Nhật ký cập nhật

| Ngày | Thay đổi |
|---|---|
| 2026-07-02 | Tạo bản đầu. P0 done, P1 (money-core) plan đã duyệt, sẵn sàng implement |
| 2026-07-02 | Bộ plan kỹ thuật toàn hệ thống (backend-only, FE tách phạm vi): `master/20260702-technical-architecture.md` + `details/20260702-p2..p6-*.md`. Amend §5: đường QR quay lại "mọi luồng qua ví" nhờ thiết kế topup-first (plan P2 §2.5) — không cần ngoại lệ QR-thẳng-escrow nữa |
