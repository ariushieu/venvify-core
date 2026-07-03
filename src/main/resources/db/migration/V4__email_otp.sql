-- V4: xác thực email bằng OTP 6 số thay cho link.
-- Bỏ unique trên hash (OTP 6 số của hai user có thể trùng nhau), đổi tên cột
-- cho đúng nghĩa và thêm bộ đếm số lần nhập sai để chặn brute force.

alter table email_verification_tokens
    drop index uq_evt_hash;

alter table email_verification_tokens
    rename column token_hash to otp_hash;

alter table email_verification_tokens
    add column attempts int not null default 0 after otp_hash;
