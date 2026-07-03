package com.venvify.venvifycore.social.dto;

/**
 * Trang storefront public của host (plan P3 §2.4 + P6 §1–2 đắp follower/rating).
 * Danh sách event đi endpoint con {@code GET /hosts/{handle}/events} (paged riêng).
 */
public record HostStorefrontResponse(
        String handle,
        String name,
        String avatarUrl,
        String bio,
        long followerCount,
        long reviewCount,
        double avgRating,
        long upcomingEventCount
) {
}
