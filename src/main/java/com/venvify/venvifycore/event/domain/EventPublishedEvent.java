package com.venvify.venvifycore.event.domain;

/** Domain event: event vừa PUBLISHED — listener P6 fan-out notification cho follower của host. */
public record EventPublishedEvent(Long eventId) {
}
