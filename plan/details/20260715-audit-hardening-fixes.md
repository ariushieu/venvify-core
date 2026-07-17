# Audit Hardening — Auth transaction persistence

**Ngày tạo:** 2026-07-15 · **Cập nhật:** 2026-07-17 · **Trạng thái:** ✅ ĐÃ DUYỆT 2026-07-17 · **Người duyệt:** chủ dự án
**Nguồn:** xác minh trực tiếp trên `master` tại `d368c33`.
**Phạm vi slice:** hai security control trong `AuthService`; không đổi API contract, schema hay nghiệp vụ tiền.

> Draft ngày 2026-07-15 bị ngắt giữa F2 và có nhắc tới các finding F3–F9 nhưng không lưu đủ bằng chứng. Bản này cố ý thu hẹp về F1–F2 đã đọc trọn code path; không suy diễn các finding bị mất. Nếu cần audit phần còn lại, lập slice riêng sau khi auth đã khóa chặt.

---

## 0. Kết luận xác minh

Cả hai finding đều là lỗi thật do `UnauthorizedException` kế thừa `RuntimeException`. Spring mặc định đánh dấu rollback transaction khi exception thoát khỏi method:

- `AuthService.refresh`: bulk update thu hồi toàn bộ refresh token chạy trước khi throw, nhưng bị rollback cùng transaction.
- `AuthService.verifyEmail`: dirty checking của `attempts`/`usedAt` chạy trước khi throw, nhưng cũng bị rollback.

Unit test Mockito hiện chỉ thấy method repository được gọi hoặc object trong RAM đã đổi; chúng không đi qua Spring transaction proxy nên không phát hiện rollback DB.

## 1. F1 — Refresh-token reuse không persist revoke

### Hiện trạng

`AuthService.refresh` gặp token đã revoke sẽ gọi `revokeAllActiveByUserId(...)`, sau đó throw `UnauthorizedException("Refresh token reuse detected")`. Vì method đang dùng `@Transactional` mặc định, lệnh revoke bị rollback. Response vẫn là 401 nên lỗi không lộ qua API, nhưng các refresh token còn lại của user tiếp tục dùng được.

### Sửa đã chọn

Đổi transaction boundary đúng tại method:

```java
@Transactional(noRollbackFor = UnauthorizedException.class)
public AuthResponse refresh(String refreshToken) { ... }
```

Chỉ mở `noRollbackFor` cho `UnauthorizedException` ở riêng method này:

- Nhánh reuse: revoke-all được commit rồi caller vẫn nhận 401.
- Token không tồn tại/hết hạn: không có mutation nên commit rỗng.
- `ForbiddenException`, lỗi persistence và lỗi không dự kiến vẫn rollback như cũ.
- Luồng refresh hợp lệ vẫn rotate token cũ và tạo token mới trong một transaction.

Không dùng `REQUIRES_NEW`: không cần thêm bean/proxy phụ, không tạo nested transaction và không thay đổi rule R18 của money-core.

### Test bắt buộc

Integration test qua Spring proxy + MySQL thật:

1. Seed một user với token A đã revoke và token B còn active.
2. Gọi `refresh(A)` và assert trả `UnauthorizedException`/reuse.
3. Mở transaction đọc mới, assert token B đã có `revoked_at`.
4. Assert không phát sinh refresh token mới.

Unit test hiện tại giữ lại để kiểm tra message và interaction; integration test mới là chốt chặn regression transaction.

## 2. F2 — Sai OTP không persist attempts/usedAt

### Hiện trạng

`AuthService.verifyEmail` tăng `attempts`, lần thứ 5 set `usedAt`, rồi throw `UnauthorizedException`. Toàn bộ thay đổi bị rollback nên DB luôn giữ `attempts = 0`; giới hạn năm lần thử chỉ tồn tại trong object của unit test.

Ngoài rollback, query hiện không khóa row OTP. Nhiều request sai đồng thời có thể cùng đọc một giá trị `attempts`, gây optimistic conflict hoặc làm bộ đếm không phản ánh đủ số lần thử.

### Sửa đã chọn

1. Đổi transaction boundary đúng tại method:

```java
@Transactional(noRollbackFor = UnauthorizedException.class)
public AuthResponse verifyEmail(String email, String otp) { ... }
```

2. Thêm `@Lock(LockModeType.PESSIMISTIC_WRITE)` vào query lấy OTP active mới nhất trong `EmailVerificationTokenRepository`.

Hành vi sau sửa:

- Mỗi lần OTP sai được serialize trên row token, tăng đúng một lần và commit dù API trả 401.
- Lần sai thứ 5 commit cả `attempts = 5` và `used_at`.
- Request sau đó không lấy lại token đã khóa; trả lỗi expired/request-new-code như hiện tại.
- OTP đúng commit `email_verified`, `used_at` và refresh token mới atomically.
- `ForbiddenException`, lỗi persistence và lỗi không dự kiến vẫn rollback.

Không dùng atomic bulk update riêng vì luồng thành công còn phải mutate `User`, đánh dấu OTP đã dùng và phát refresh token trong cùng transaction. Row lock giữ state machine đơn giản và chặn verify đồng thời.

### Test bắt buộc

Integration test qua Spring proxy + MySQL thật:

1. Sai một lần → bắt 401, đọc transaction mới thấy `attempts = 1`, `used_at IS NULL`.
2. Sai đủ năm request riêng → `attempts = 5`, `used_at IS NOT NULL`.
3. Mã đúng sau bốn lần sai → user được verify, OTP used và refresh token được tạo.
4. Mã đúng sau khi token đã khóa → bị từ chối, user vẫn chưa verified.
5. Hai request verify đồng thời trên cùng OTP được serialize; không có hai lần phát token thành công.

## 3. File dự kiến thay đổi khi implement

Một vertical slice, tối đa ba file:

1. `user/service/AuthService.java` — transaction rollback policy cho `refresh` và `verifyEmail`.
2. `user/repository/EmailVerificationTokenRepository.java` — pessimistic write lock cho OTP query.
3. `user/service/AuthServiceTransactionTest.java` — integration test persistence/concurrency trên MySQL.

Không cần migration, dependency hay thay đổi controller/DTO.

## 4. Điều kiện DONE

- Reuse token trả 401 nhưng revoke-all tồn tại sau khi request kết thúc.
- Mỗi OTP sai được persist; lần thứ 5 khóa token thật trong DB.
- Verify OTP đồng thời không phát hai phiên thành công.
- Refresh hợp lệ, OTP đúng, login/logout và resend-verification không đổi contract.
- User chạy test/build theo AGENTS.md §9; agent không tự chạy Maven.

## 5. Quyết định đã duyệt

- **Đ-A1 — DUYỆT 2026-07-17:** dùng `noRollbackFor = UnauthorizedException.class` ở đúng hai method thay cho bean `REQUIRES_NEW`.
- **Đ-A2 — DUYỆT 2026-07-17:** dùng pessimistic row lock cho OTP verification để giới hạn thử vẫn đúng dưới concurrent request.
- **Đ-A3 — DUYỆT 2026-07-17:** integration test dùng MySQL thật theo chiến lược test hiện tại; không thêm H2/Testcontainers dependency.

Implementation bám đúng phạm vi ba file ở §3.
