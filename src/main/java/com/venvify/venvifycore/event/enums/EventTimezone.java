package com.venvify.venvifycore.event.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Múi giờ hiển thị của event — danh sách chọn cố định để host khỏi phải gõ IANA id.
 * Lưu tên hằng (vd VIETNAM) trong DB; {@link #zoneId} là IANA zone id thật dùng để render
 * giờ treo tường từ mốc UTC. VN-first, kèm các múi giờ phổ biến của attendee quốc tế.
 */
@Getter
@RequiredArgsConstructor
public enum EventTimezone {

    VIETNAM("Asia/Ho_Chi_Minh"),      // GMT+7
    SINGAPORE("Asia/Singapore"),      // GMT+8
    TOKYO("Asia/Tokyo"),              // GMT+9
    INDIA("Asia/Kolkata"),            // GMT+5:30
    DUBAI("Asia/Dubai"),              // GMT+4
    UTC("UTC"),                       // GMT+0
    LONDON("Europe/London"),          // GMT+0/+1 (DST)
    PARIS("Europe/Paris"),            // GMT+1/+2 (DST)
    NEW_YORK("America/New_York"),     // GMT-5/-4 (DST)
    LOS_ANGELES("America/Los_Angeles"), // GMT-8/-7 (DST)
    SYDNEY("Australia/Sydney");       // GMT+10/+11 (DST)

    /** IANA zone id tương ứng (vd "Asia/Ho_Chi_Minh"). */
    private final String zoneId;
}
