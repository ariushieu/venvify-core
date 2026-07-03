package com.venvify.venvifycore.booking.domain;

import com.venvify.venvifycore.booking.enums.TicketTransferStatus;

/**
 * Domain event: offer chuyển nhượng đổi trạng thái (tạo mới = PENDING). Notification
 * listener (P6) dựa vào {@code status} chọn template + người nhận thông báo;
 * load chi tiết theo id ở tx riêng. Một event duy nhất thay vì 5 class rời —
 * state machine chỉ có 1 trục trạng thái.
 */
public record TicketTransferChangedEvent(Long transferId, TicketTransferStatus status) {
}
