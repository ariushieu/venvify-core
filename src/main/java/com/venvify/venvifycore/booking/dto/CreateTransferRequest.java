package com.venvify.venvifycore.booking.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Tạo offer chuyển nhượng (plan P3 §1.5): người nhận qua email HOẶC host handle
 * (đúng 1 trong 2 — check ở service); price 0 = tặng, tối đa = price_paid (R1 service check).
 */
public record CreateTransferRequest(
        String toUserEmail,
        String toUserHandle,
        @PositiveOrZero(message = "Price must not be negative") Long price
) {
}
