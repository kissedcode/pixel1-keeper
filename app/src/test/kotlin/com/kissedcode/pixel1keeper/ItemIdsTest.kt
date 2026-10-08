package com.kissedcode.pixel1keeper

import com.kissedcode.pixel1keeper.work.Scheduling
import com.kissedcode.pixel1keeper.yandex.ItemIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemIdsTest {
    private val cutoff = 1791410400000L // 2026-10-08T00:00+02:00

    @Test fun etimeFromItemId() {
        val id = "2_0000001791392334000_28afc550d3ca90c804114fe0a13e0ba83308eb069a51c0d463e5904e101b3231_1791392369057579"
        assertEquals(1791392334000L, ItemIds.etimeMs(id))
        assertTrue(ItemIds.etimeMs(id)!! < cutoff) // снято 07.10 — не переносится
        val after = "2_0000001791411766000_d47e74dd_1791413749747419"
        assertTrue(ItemIds.etimeMs(after)!! >= cutoff) // снято 08.10 00:22 — переносится
        assertNull(ItemIds.etimeMs("garbage"))
    }

    @Test fun normalizePath() {
        assertEquals("/photounlim/2026-10-07 18-58-54.JPG", ItemIds.normalizePath("photounlim:/2026-10-07 18-58-54.JPG"))
        assertEquals("/photounlim/x.JPG", ItemIds.normalizePath("/photounlim/x.JPG"))
        assertEquals("2026-10-07 18-58-54.JPG", ItemIds.fileName("/photounlim/2026-10-07 18-58-54.JPG"))
    }

    @Test fun clusterTo() {
        assertEquals(1791369120000L, ItemIds.clusterToMs("0000001791359875000_0000001791369120000"))
    }

    @Test fun window() {
        assertTrue(Scheduling.inWindow(0, 0, 7))
        assertTrue(Scheduling.inWindow(6, 0, 7))
        assertFalse(Scheduling.inWindow(7, 0, 7))
        assertTrue(Scheduling.inWindow(23, 22, 6))
        assertTrue(Scheduling.inWindow(3, 22, 6))
        assertFalse(Scheduling.inWindow(12, 22, 6))
    }
}
