package com.taportal.domain.events;

/** Who set an interview's time, and how. */
public enum BookingOrigin {
    /** The candidate replied to a text message. */
    TEXT,
    /** The candidate used the self-schedule page. */
    WEB_PAGE,
    /** The candidate picked a time in web chat. */
    CHAT,
    /** The hiring team booked it. */
    TEAM
}
