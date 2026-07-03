package com.venvify.venvifycore.event.enums;

/**
 * Sort cho discovery (plan P3 §2.1). UPCOMING = start_time ASC (mặc định);
 * NEWEST = id DESC (R15 — id thay cho created_at).
 */
public enum EventListSort {
    UPCOMING,
    NEWEST
}
