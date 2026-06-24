package com.venvify.venvifycore.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Sinh UUID phiên bản 7 (time-ordered) — 48 bit timestamp mili-giây ở đầu nên
 * index trên cột public_id có tính cục bộ tốt hơn UUID v4 ngẫu nhiên (theo SPEC §5.5, D7).
 * Nếu sau này muốn dùng thư viện chuyên dụng, chỉ cần thay phần thân generate().
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        long timestamp = System.currentTimeMillis();

        // most significant bits: [48 bit timestamp][4 bit version = 0111][12 bit random]
        long msb = (timestamp & 0xFFFFFFFFFFFFL) << 16;
        msb |= 0x7000L;                       // version 7
        msb |= RANDOM.nextInt(0x1000) & 0xFFF; // 12 bit rand_a

        // least significant bits: [2 bit variant = 10][62 bit random]
        long lsb = RANDOM.nextLong();
        lsb &= 0x3FFFFFFFFFFFFFFFL;
        lsb |= 0x8000000000000000L;           // variant RFC 4122

        return new UUID(msb, lsb);
    }

    public static String generateString() {
        return generate().toString();
    }
}
