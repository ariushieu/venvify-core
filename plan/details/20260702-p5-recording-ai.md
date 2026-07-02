# Plan kỹ thuật P5 — Replay + AI pipeline (transcript → summary)

**Ngày tạo:** 2026-07-02 · **Trạng thái:** ⏳ CHỜ DUYỆT (khung — refresh §9 đầu phase; pricing/model AI trôi nhanh) · **Phase:** P5 (T11/2026–T1/2027)
**Tiền đề:** P4 xong — recording mp4 + audio OGG đã nằm trên R2, `RecordingReadyEvent` bắn khi egress xong, bảng `questions` có dữ liệu Q&A thật.
**Bám:** master doc (§8 queue = bảng DB, không broker).

---

## 0. Pipeline tổng

```
RecordingReadyEvent ──▶ ai_jobs(TRANSCRIBE) ──poller──▶ Whisper(audio.ogg từ R2)
                                                            │ transcript (text + segments JSON) → R2
                                                            ▼
                                        ai_jobs(SUMMARIZE) ──▶ Claude API
                                                            │ summary md + top_questions JSON
                                                            ▼
                                              summaries READY ──▶ notification + email attendees
```

Toàn pipeline **async ngoài request**, idempotent, sống sót app-restart nhờ bảng `ai_jobs`.

## 1. Bảng `ai_jobs` (V8) — DB-backed queue chuẩn prod tối giản

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id, created_at, updated_at | | |
| type | VARCHAR(20) NOT NULL | TRANSCRIBE/SUMMARIZE (cột enum = VARCHAR — master §6) |
| ref_id | BIGINT NOT NULL | recording_id / summary_id |
| status | VARCHAR(20) NOT NULL | QUEUED/RUNNING/SUCCEEDED/FAILED/DEAD — FAILED = chờ retry; DEAD = hết attempts |
| attempts | INT DEFAULT 0 | max 3 |
| next_attempt_at | DATETIME(6) | backoff 1' → 5' → 25' |
| last_error | VARCHAR(1000) | |

`UNIQUE(type, ref_id)` — enqueue đúp vô hại. **Poller 30s:** `SELECT ... WHERE status IN (QUEUED, FAILED) AND next_attempt_at <= now ORDER BY id LIMIT 2 FOR UPDATE SKIP LOCKED` → RUNNING → xử lý **ngoài transaction claim** (gọi API ngoài — RULE master §7) → SUCCEEDED/FAILED(+backoff)/DEAD(+Sentry). App chết giữa RUNNING → job kẹt: poller coi RUNNING quá 30' là FAILED (lease timeout).

## 2. Transcribe

- **`TranscriptionProvider` interface** — 2 impl chọn bằng config `app.ai.transcription-provider: openai|groq`: OpenAI `whisper-1` (chuẩn, ~$0.006/phút ≈ 9k VND/2h) / Groq `whisper-large-v3-turbo` (rẻ + nhanh hơn nhiều lần — khuyến nghị thử trước, O-MP4).
- Input: presigned GET `recordings.audio_url` (OGG từ P4 — không phải đụng mp4). File > 25MB (giới hạn upload API) → chia đoạn theo thời lượng (ffmpeg KHÔNG có trên VPS1 — dùng API hỗ trợ URL/chunk của provider; nếu bắt buộc chia local → cân nhắc chạy transcribe theo đoạn OGG do egress cắt sẵn `segment duration` — chốt khi đọc docs egress, ghi vào §9).
- Output: text đầy đủ + segments (timestamps) JSON → upload R2 `transcripts/{event_public_id}.json` + `.txt`; `summaries` row PENDING với `transcript_url`. Ngôn ngữ: `vi` hint.
- Guard: `app.ai.max-duration-minutes: 180` — quá thì DEAD luôn + notification host "quá dài, không auto-summary" (cost control).

## 3. Summarize (Claude API)

- Model: `app.ai.summary-model` (env; mặc định dòng Sonnet hiện hành — chốt model id lúc code theo pricing; ~2h talk ≈ 30–40k token in + 2k out ≈ $0.1–0.2/event với Sonnet).
- Prompt template (resource file, tiếng Việt): input = transcript (+ top 10 questions theo upvote từ bảng `questions` — cross-module qua service P4) → output JSON: `{summary_md (các phần: tóm tắt 3 câu, nội dung chính theo mục, key takeaways), top_qa: [{question, answer_from_transcript}]}`.
- Transcript > ~150k token (talk > 8h — gần như không xảy ra): cắt đầu-cuối + note "partial".
- Ghi `summaries`: summary_content (markdown), top_questions (JSON), model_used, status READY → `SummaryReadyEvent` → notification + email attendee ATTENDED + host.
- **`SummaryProvider` interface** — mock trong test; gọi thật qua Anthropic SDK Java hoặc HTTP thuần (chốt lúc code — HTTP thuần ít dependency).

## 4. Replay & quyền xem

- `GET /events/{id}/recording` → presigned GET mp4 TTL 1h — quyền: host / booking ATTENDED / CONFIRMED (mua nhưng vắng vẫn được xem — giá trị vé). 404 nếu chưa READY.
- `GET /events/{id}/summary` → summary_content + top_questions — cùng quyền. (Public hóa summary làm marketing = để sau, mặc định private.)
- R2: bucket private duy nhất, prefix `recordings/`, `audio/`, `transcripts/`; Spring cầm key (P4 đã có config); **AWS SDK v2 S3 client** dependency vào pom ở phase này (P4 mới chỉ egress tự upload).

## 5. Admin/dev hooks

`POST /admin/ai-jobs/{id}/retry` (DEAD → QUEUED, reset attempts — P6 admin; trước đó dev chạy SQL) · `app.ai.enabled: false` = kill-switch toàn pipeline (flag config — sự cố cost thì tắt ngay không cần deploy).

## 6. Thay đổi hệ thống

pom: AWS SDK v2 `s3` (+ HTTP client Anthropic nếu dùng SDK) · V8: `ai_jobs` · config: `app.ai.{enabled, transcription-provider, summary-model, max-duration-minutes}` + secrets `OPENAI_API_KEY|GROQ_API_KEY`, `ANTHROPIC_API_KEY` (master §5) · jobs catalog: poller 30s (master §8 đã ghi).

## 7. Test plan

Poller: claim SKIP LOCKED không đúp dưới 2 thread · lease timeout nhặt lại RUNNING kẹt · backoff đúng mốc · DEAD sau 3 + Sentry · enqueue đúp bị UNIQUE nuốt · provider mock: transcribe fail → retry; summarize nhận đúng prompt (golden file) · guard duration · quyền replay/summary matrix (host/ATTENDED/CONFIRMED/ngoài) · presigned TTL đúng · kill-switch chặn poller.

## 8. Điều kiện DONE của phase

Event thật end → ≤ 5' sau (SPEC mục tiêu) attendee nhận notification + email, mở được replay và summary tiếng Việt đọc được, chi phí AI/event ghi nhận ≤ 10k VND. Job fail giữa chừng tự hồi không cần đụng tay.

## 9. Refresh checklist đầu phase

- [ ] Pricing + model id hiện hành: Whisper OpenAI vs Groq (O-MP4), dòng Claude (Sonnet đời mới nhất đủ tốt cho summary).
- [ ] Giới hạn file/URL input của API transcribe thời điểm đó; egress có cắt segment OGG được không (§2) — chốt phương án file dài.
- [ ] Đối chiếu `recordings.audio_url` P4 đã ship đúng chưa; `RecordingReadyEvent` payload thật.
- [ ] Số migration V thật; secrets đã vào VPS (.env) + GitHub (nếu CI test tích hợp — mặc định mock, không cần).
