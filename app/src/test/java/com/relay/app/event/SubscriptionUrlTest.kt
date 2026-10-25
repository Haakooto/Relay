package com.relay.app.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionUrlTest {
    @Test fun httpsIsKept() =
        assertEquals("https://example.com/cal.ics", SubscriptionUrl.normalize("  https://example.com/cal.ics "))

    @Test fun webcalIsReadAsHttps() =
        assertEquals("https://example.com/basic.ics", SubscriptionUrl.normalize("webcal://example.com/basic.ics"))

    @Test fun plainHttpIsRefused() = assertNull(SubscriptionUrl.normalize("http://example.com/cal.ics"))

    @Test fun otherSchemesAreRefused() {
        assertNull(SubscriptionUrl.normalize("file:///sdcard/cal.ics"))
        assertNull(SubscriptionUrl.normalize("content://x/cal.ics"))
        assertNull(SubscriptionUrl.normalize("example.com/cal.ics"))
    }

    @Test fun noHostIsRefused() {
        assertNull(SubscriptionUrl.normalize("https://"))
        assertNull(SubscriptionUrl.normalize("https:///cal.ics"))
    }

    @Test fun emptyAndOverlongAreRefused() {
        assertNull(SubscriptionUrl.normalize("   "))
        assertNull(SubscriptionUrl.normalize("https://example.com/" + "a".repeat(2000)))
    }

    @Test fun spacesInsideAreRefused() = assertNull(SubscriptionUrl.normalize("https://exa mple.com/cal.ics"))
}
