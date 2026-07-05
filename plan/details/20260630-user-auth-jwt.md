# Plan: Slice User + Auth (JWT / Spring Security)

**Ngày tạo:** 2026-06-30 · **Trạng thái:** ✅ ĐÃ IMPLEMENT (2026-06-30) · **Người duyệt:** chủ dự án
**Liên quan:** `SPEC.md` §5.2 (auth giữa 2 backend), CLAUDE.md §3–§5, ERD D1 (multi-role)

> ⚠️ Bản thiết kế để duyệt, CHƯA sinh code. Đây là **nền auth** — mọi slice sau (booking/payment/transfer) phụ thuộc.

---

## 0. Quyết định đã chốt
- **A1 — Thư viện:** `jjwt` (io.jsonwebtoken), ký **HS256 khóa chia sẻ** (Spring phát, Node verify cùng key — SPEC §5.2).
- **A2 — Token:** **Access + Refresh**. Access ngắn (15'), refresh dài (7 ngày).
- **A3 — Email verify (CẬP NHẬT):** **bật ngay** qua **Resend**. Đăng ký → gửi email xác thực; **chặn login** tới khi verify (login trả 403 `Email not verified`). Có endpoint gửi lại. Token xác thực lưu hash, dùng một lần, hạn 24h (bảng `email_verification_tokens`).

---

## 1. Dependencies (`pom.xml`)
- `spring-boot-starter-security` — **nhớ exclude `spring-boot-starter-logging`** (như các starter khác, tránh xung đột Logback/Log4j2).
- `io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl:0.12.6` (runtime), `jjwt-jackson:0.12.6` (runtime).

## 2. Config & secret (KHÔNG hard-code)
`application.yaml` đọc từ env:
```yaml
jwt:
  secret: ${SECRET_KEY}              # HS256, >= 256-bit (32+ ký tự ngẫu nhiên)
  access-token-expiration: 900000    # 15 phút (ms)
  refresh-token-expiration: 604800000 # 7 ngày (ms)
```
`SECRET_KEY` để trong `.env` (đã gitignore). Mật khẩu hash **BCrypt**.

## 3. Thiết kế token
- **Access token (JWT):** `sub = user.public_id` (KHÔNG lộ id nội bộ), claim `roles` (vd `["ATTENDEE"]`), `iat`/`exp`. Node verify cùng secret → đọc `sub` + `roles`.
- **Stateless cho access:** mỗi request build `Authentication` thẳng từ claims, **không** truy DB (access ngắn hạn nên đủ an toàn; user bị SUSPENDED sẽ chặn ở lần refresh kế).
- **Refresh token — STATEFUL (khuyến nghị):** chuỗi ngẫu nhiên entropy cao, **lưu hash (SHA-256) trong DB** (bảng `refresh_tokens`). Cho phép **thu hồi** (logout), **xoay vòng** (rotation) + **phát hiện tái dùng** (token đã revoke mà bị dùng lại → revoke toàn bộ token của user, nghi bị lộ).
  - *Vì sao stateful:* platform tiền cần thu hồi phiên được. Stateless refresh (JWT thuần) không revoke được trước hạn.

### 3.1 Bảng MỚI `refresh_tokens` (module `user/`) → migration **V3**
| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| id, public_id | | | base |
| user_id | BIGINT | FK→users.id, NOT NULL | |
| token_hash | VARCHAR(64) | UNIQUE, NOT NULL | SHA-256 hex của refresh token |
| expires_at | DATETIME(6) | NOT NULL | |
| revoked_at | DATETIME(6) | NULL | NULL = còn hiệu lực |
| created_at, updated_at, version | | | base |

- Index: `INDEX(user_id)`, `UNIQUE(token_hash)`.
- Migration `V3__refresh_tokens.sql` (CREATE TABLE). Không đụng V1/V2.

## 4. Endpoints (`/api/v1`)
| Method | Path | Body | Trả về |
|---|---|---|---|
| POST | `/auth/register` | `{email, password, fullName}` | `UserResponse` + message (KHÔNG token — chờ verify) |
| POST | `/auth/login` | `{email, password}` | `AuthResponse` (403 nếu chưa verify) |
| POST | `/auth/refresh` | `{refreshToken}` | `AuthResponse` (xoay vòng) |
| POST | `/auth/logout` | `{refreshToken}` | 200 (revoke token) |
| GET | `/auth/verify-email` | `?token=…` | 200 "Email verified" |
| POST | `/auth/resend-verification` | `{email}` | 200 (message trung lập, không lộ email tồn tại) |
| GET | `/users/me` | — (Bearer) | `UserResponse` |

- `AuthResponse = { accessToken, refreshToken, tokenType="Bearer", expiresIn, user: UserResponse }`.
- Tất cả bọc trong `ApiResponse` (message tiếng Anh) như chuẩn hiện có.

### 4.1 Luồng register
1. Validate (email đúng định dạng, password ≥8…), check email chưa tồn tại → 409 nếu trùng.
2. Tạo `User` (status ACTIVE, email_verified=false, role mặc định **ATTENDEE**), hash BCrypt.
3. **Tạo `Wallet` (account_type=USER, balance=0)** cho user — gắn với double-entry D12.
4. Tạo token xác thực (lưu hash vào `email_verification_tokens`) + gửi email qua Resend.
5. Trả `UserResponse` + message — **KHÔNG phát JWT**; user phải verify rồi mới login được.

> Migration thực tế: **`V3__auth_tokens.sql`** tạo CẢ `refresh_tokens` lẫn `email_verification_tokens`. Thêm dep `spring-boot-starter-security` + `jjwt 0.12.6`; config `jwt.*`, `resend.*`, `app.*` trong `application.yaml` (đọc env `SECRET_KEY`, `RESEND_API_KEY`, `RESEND_FROM_EMAIL`).

### 4.2 Luồng login / refresh
- Login: load user theo email → `BCrypt.matches` → phát token. Sai → 401.
- Refresh: hash token gửi lên → tra `refresh_tokens` → check chưa revoke + chưa hết hạn + user còn ACTIVE → **revoke token cũ, phát cặp mới** (rotation). Token đã revoke mà bị dùng lại → revoke hết của user.

## 5. Spring Security
- **Stateless** (`SessionCreationPolicy.STATELESS`), tắt CSRF (API thuần token).
- **Permit:** `/auth/**`, swagger (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`), `/` (redirect). Còn lại `authenticated()`.
- `JwtAuthenticationFilter` (OncePerRequest): lấy `Bearer`, verify, set `SecurityContext` (authorities `ROLE_ATTENDEE/HOST/ADMIN`).
- `AuthenticationEntryPoint` → 401 JSON (ApiResponse); `AccessDeniedHandler` → 403 JSON.
- `BCryptPasswordEncoder` bean; `AuthenticationManager` + `CustomUserDetailsService` (load theo email) cho login.

## 6. Files dự kiến (vertical slice — to hơn slice thường nhưng 1 feature)
```
common/config/SecurityConfig.java
common/security/JwtTokenProvider.java
common/security/JwtAuthenticationFilter.java
common/security/JwtAuthenticationEntryPoint.java
common/security/JwtAccessDeniedHandler.java
common/security/CustomUserDetails.java (+ CustomUserDetailsService.java)
user/dto/{RegisterRequest, LoginRequest, RefreshRequest, AuthResponse}.java
user/service/AuthService.java
user/controller/AuthController.java
user/controller/UserController.java        # /users/me
user/entity/RefreshToken.java
user/repository/RefreshTokenRepository.java
src/main/resources/db/migration/V3__refresh_tokens.sql
pom.xml (deps) · application.yaml (jwt.*) · .env (SECRET_KEY)
```

## 7. OPEN — cần bạn xác nhận
- **AO1 — Refresh stateful + bảng `refresh_tokens`:** đồng ý không? (Khuyến nghị có, để revoke/logout được. Nếu muốn gọn tối đa cho MVP thì làm stateless không cần bảng — báo mình.)
- **AO2 — Rule password:** tối thiểu mấy ký tự? Khuyến nghị **≥ 8**, có chữ + số. → chốt mức.
- **AO3 — Tạo ví lúc register:** đồng ý tạo `Wallet` USER ngay khi đăng ký không? (Khuyến nghị có.)
- **AO4 — OAuth Google:** để **sau** slice này (schema đã có sẵn cột oauth). Xác nhận.

## 8. Sau khi duyệt
Code 1 lượt cả slice (security + auth + /users/me), kèm `V3`. Xong báo file đã đổi; bạn run verify (Flyway V3 + đăng ký/đăng nhập thử qua Swagger) rồi commit.
