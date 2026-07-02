# CLAUDE.md

Hướng dẫn cho Claude Code khi làm việc trong repo này. **Các rule ở đây override hành vi mặc định — tuân thủ nghiêm ngặt mọi task.**

## Dự án

**Online Event Platform** — nền tảng tổ chức & tham dự sự kiện trực tuyến (workshop, AMA, talk show, demo day). Đồ án tốt nghiệp **solo, 1 năm**, định hướng startup thật.

> **Đặc tả đầy đủ ở [`SPEC.md`](./SPEC.md) — ĐỌC TRƯỚC khi sinh code hoặc đề xuất kiến trúc.**
> SPEC.md là nguồn sự thật. Tôn trọng mọi `DECISION:`; hỏi user trước khi đụng `OPEN:`; tuân thủ mọi `RULE:`.

## Quyết định kiến trúc đã chốt (không tự ý đổi)

- **Backend chính:** Spring Boot 3.x (Spring Security JWT, Spring Data JPA) — nguồn sự thật duy nhất.
- **Realtime:** Node.js + Socket.IO — relay thuần, KHÔNG sở hữu nghiệp vụ. Hỏi Spring để authorize.
- **DB:** MySQL (InnoDB) + Redis. **Không dùng MongoDB / PostgreSQL.**
- **Auth:** JWT stateless. Không session/cookie thuần.
- **Frontend:** React + TailwindCSS (SPA).
- **Thứ tự build:** hoàn thành toàn bộ core Spring trước → rồi mới sang realtime Node.
- WebRTC SFU (LiveKit vs mediasoup) còn `OPEN` — hỏi user trước khi code phần này.

---

## 1. Hành vi mặc định (ƯU TIÊN CAO NHẤT)

- **Ưu tiên sửa code trực tiếp** hơn là viết tài liệu phân tích trung gian, trừ khi task rất phức tạp.
- **Không tạo file tổng hợp** (`analysis.md`, `overview.md`, `notes.md`...). Nếu cần plan, theo §2.
- **Không bao giờ hard-code dữ liệu nhạy cảm** (API key, password, token, secret). Lưu trong `.env`, thêm vào `.gitignore`.
- **Code ngay khi đủ context.** Không sản xuất ghi chú thăm dò/tóm tắt nếu không được yêu cầu.
- **Không thuật lại bước thăm dò** ("giờ tôi sẽ đọc controller..."). Sau khi sửa xong, tóm tắt ngắn các file đã đổi. Không thông báo việc tương lai — hoặc làm, hoặc dừng.

## 2. File Plan

- **Vị trí:** mọi file plan/phân tích/thiết kế lưu trong `/plan` ở gốc dự án. Không dùng `.claude/plans` hay thư mục ẩn. Chia 2 folder con:
  - `/plan/master` — tài liệu tầm hệ thống, viết 1 lần dùng lâu dài: ERD toàn cục, master roadmap (module nào, thứ tự, ranh giới MVP, quyết định xuyên suốt).
  - `/plan/details` — plan chi tiết per-slice (service design, DDL, rule, test), viết just-in-time ngay trước khi code slice đó.
- **Đặt tên:** `YYYYMMDD-[ten-tinh-nang].md` (vd `20260331-user-authentication.md`).
- **Quy trình** trước khi làm feature lớn: (1) tạo file plan trong `/plan` → (2) chờ user duyệt → (3) chỉ implement sau khi được duyệt.
- **Đầu session mới (chưa có context):** đọc `master/20260702-master-roadmap.md` trước tiên — nó cho biết dự án đang ở phase nào, plan nào đã duyệt/chưa duyệt, việc gì kế tiếp. Không nhận task nào mà bỏ qua bước này.
- **Trước khi code BẤT KỲ slice nào:** đọc `master/20260702-technical-architecture.md` — đó là chuẩn ràng buộc (stack, ranh giới module, RULE transaction/security/enum/migration). Code mâu thuẫn doc đó = sai, kể cả khi plan chi tiết nói khác (sửa doc trước, code sau).
- **Plan phase (details/p2..p6) là KHUNG viết trước nhiều tháng:** trước khi code phase nào, BẮT BUỘC chạy mục "Refresh checklist đầu phase" trong doc đó (đối chiếu docs provider, số migration thật, code đã trôi) và chốt các mục "Đề xuất cần chốt" (Đ-*) với user. Không code thẳng từ doc khung chưa refresh.

## 3. Nguyên tắc kỹ thuật (SOLID + Clean Architecture — BẮT BUỘC)

### SOLID
- **S** — mỗi class/module một trách nhiệm. Controller chỉ điều phối, không chứa business logic.
- **O** — mở rộng qua interface/abstraction, không sửa code đang chạy ổn.
- **L** — implementation thay thế được interface mà không phá hành vi.
- **I** — interface nhỏ, chuyên biệt.
- **D** — phụ thuộc vào abstraction, không vào concrete. Inject qua constructor (xem §4), không `new` trực tiếp service.

### Clean Architecture — phụ thuộc chỉ hướng vào trong
`Controller → Service → Repository → Domain`. Tầng ngoài không bị tầng trong phụ thuộc ngược.

## 4. Cấu trúc & chuẩn code Spring Boot

### Cấu trúc package (package-by-feature)
Mỗi module nghiệp vụ chứa đủ tầng của nó; phần dùng chung nằm trong `common/`.
```
com.venvify.venvifycore/
├── common/
│   ├── entity/      # BaseEntity, SoftDeletableEntity
│   ├── exception/   # Custom exceptions + @RestControllerAdvice
│   ├── dto/         # ApiResponse, PagedResponse (wrapper dùng chung)
│   ├── config/      # @Configuration (JPA auditing...)
│   └── util/        # Helper (UuidV7...)
├── user/            # entity/ enums/ repository/ dto/ mapper/ service/ controller/
├── event/
├── booking/
├── wallet/          # wallet, ledger, transaction, escrow (gộp domain tiền)
├── room/
├── interaction/     # poll, question, chat
├── content/         # recording, summary
├── social/          # follow, review
└── notification/
```
Trong mỗi module, tầng vẫn tách rõ: `controller → service → repository → entity`, phụ thuộc chỉ hướng vào trong. Enum đặt trong `{module}/enums/`.

### Dependency Injection
- **Không dùng `@Autowired`.** Luôn dùng **constructor injection** qua Lombok `@RequiredArgsConstructor`.
- Khai báo dependency là `private final`.
```java
// Đúng
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
}
```

### Lombok & MapStruct
- **Lombok:** `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` trên entity/DTO; `@RequiredArgsConstructor` trên mọi Spring bean.
- **MapStruct:** mọi mapping DTO ↔ Entity. Mapper interface trong `mapper/`, annotate `@Mapper(componentModel = "spring")`. Không viết mapping thủ công khi MapStruct làm được.

### REST API
- Theo chuẩn RESTful: `GET/POST/PUT/PATCH/DELETE`. Resource path danh từ số nhiều: `/api/v1/users`, `/api/v1/events`.
- Dùng response wrapper nhất quán cho mọi endpoint.
- **Pagination bắt buộc:** mọi `GET` trả collection phải nhận `Pageable` và trả `Page<T>`. Không trả list không giới hạn.
```java
@GetMapping
public ResponseEntity<Page<UserResponse>> getAll(Pageable pageable) { ... }
```

### DTO & Validation
- Luôn tách **Request DTO** và **Response DTO** — không bao giờ expose JPA entity ra API.
- Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Email`...) trên Request DTO; `@Valid` ở tham số controller.

### Soft Delete
- **Không hard delete** trừ khi nghiệp vụ yêu cầu. Dùng field `isDeleted` (cột `is_deleted`).
- Lọc record đã xóa ở repository (`findByIdAndIsDeletedFalse` hoặc `@Query`).
- Endpoint `DELETE` set `isDeleted = true`, không `deleteById()`.

### Exception Handling
- Dùng `@RestControllerAdvice` tập trung. Định nghĩa custom exception (`ResourceNotFoundException`, `BadRequestException`, `ConflictException`) thay vì throw generic.
- Trả message rõ ràng + HTTP status đúng.

### Security & Secrets
- Không hard-code credential. Dùng biến môi trường trong `application.yml`:
```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET}
```
- Mọi secret trong `.env`; `.env` phải nằm trong `.gitignore`. Mật khẩu hash BCrypt.

## 5. Quy ước riêng của dự án (từ SPEC.md)

- **2 cột ID mỗi bảng:** `id` BIGINT (nội bộ, KHÔNG expose) + `public_id` VARCHAR/UUID (công khai, dùng cho API/FE). SPEC §5.5.
- **Phần tiền (SPEC §5.6) — không được sai một xu:**
  - Ledger append-only (số dư = tổng bút toán), KHÔNG cột balance cộng/trừ.
  - Idempotency cho callback VNPay/MoMo (`transaction_ref` duy nhất).
  - `SELECT ... FOR UPDATE` + transaction khi trừ ví.
  - Escrow state machine: `held → released → paid_out` / `refunded`.
- **Auth giữa 2 backend:** Spring phát JWT, Node verify cùng key + hỏi internal API để authorize vào room (SPEC §5.2).

## 6. Quy trình phát triển

- **Backend trước:** hoàn thiện & ổn định logic + API backend trước khi sang frontend.
- **API contract:** chốt DTO trước khi implement để FE làm song song được.
- **Consistency:** xác nhận DTO backend khớp interface frontend trước khi đóng feature.

## 7. Kiểm soát phạm vi (Scope Control)

- Mỗi lần làm **một vertical slice hoàn chỉnh** (Controller + Service + Repository cho MỘT entity), rồi báo file đã đổi. Không refactor cả codebase trong một lượt.
- Nếu một rule (pagination, soft delete...) cần đổi **> 3 file**, dừng và hỏi user đổi file nào trước. Không âm thầm refactor "tất cả endpoint còn lại".

## 8. Đọc file & tool

- **Targeted reads.** Trước khi đọc file, hỏi "file này có trực tiếp cần cho task không?" Nếu không → bỏ qua.
- **Layer discipline:** task Service → chỉ đọc Service + Repository/Entity/DTO liên quan. Task Controller → chỉ Controller + DTO. Không đọc lại toàn bộ backend khi API contract đã ổn định.
- **Grep trước khi mở:** dùng Grep tìm đúng class/method/field, rồi mới Read. Không đọc mù toàn file lớn — đọc theo line range.
- **Parallel reads:** cần nhiều file thì đọc một batch song song.
- **Tôn trọng `.claudeignore`** nếu có; không đọc `target/`, `.git/`, `.idea/`, `*.class`, `*.jar`.

## 9. Build & Run — user tự lo

- **Không chạy lệnh build/run/test:** không `mvn`, `gradle`, `./gradlew`, `npm run`, `java -jar`. User tự build/run/test.
- **Không side-effect shell:** không cài dependency, không khởi động server, không mutate state.
- Nếu không hoàn tất được nếu không build/test → nêu rõ đã đổi gì và user cần verify thủ công gì.
- Không commit/push trừ khi user yêu cầu.

## 10. Khi không chắc

- Đụng phần `OPEN:` trong SPEC → hỏi user, đừng tự quyết.
- Mâu thuẫn giữa yêu cầu và SPEC → nêu rõ, đừng âm thầm chọn.
