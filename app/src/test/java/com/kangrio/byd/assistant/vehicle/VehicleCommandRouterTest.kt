package com.kangrio.byd.assistant.vehicle

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull

class VehicleCommandRouterTest {

    @Test
    fun `matches a simple English fixed-value command`() {
        val matched = VehicleCommandRouter.match("open the driver window", "en")
        assertNotNull(matched)
        assertEquals("window.driver", matched!!.command.id)
        assertEquals(1, matched.value)
    }

    @Test
    fun `matching is case and punctuation insensitive`() {
        val matched = VehicleCommandRouter.match("OPEN THE DRIVER WINDOW!", "en")
        assertNotNull(matched)
        assertEquals("window.driver", matched!!.command.id)
    }

    @Test
    fun `tolerates filler words via token-set containment`() {
        val matched = VehicleCommandRouter.match("please close the passenger window now", "en")
        assertNotNull(matched)
        assertEquals("window.passenger", matched!!.command.id)
        assertEquals(2, matched.value)
    }

    @Test
    fun `matches a simple Arabic fixed-value command`() {
        val matched = VehicleCommandRouter.match("افتح نافذة السائق", "ar")
        assertNotNull(matched)
        assertEquals("window.driver", matched!!.command.id)
        assertEquals(1, matched.value)
    }

    @Test
    fun `arabic alef variants normalize to match`() {
        // Registered phrase uses plain alef (افتح); this uses hamza-under-alef (إفتح).
        val matched = VehicleCommandRouter.match("إفتح نافذة السائق", "ar")
        assertNotNull(matched)
        assertEquals("window.driver", matched!!.command.id)
    }

    @Test
    fun `resolves a numeric slot in English`() {
        val matched = VehicleCommandRouter.match("set the temperature to 22", "en")
        assertNotNull(matched)
        assertEquals("ac.temperature", matched!!.command.id)
        assertEquals(22, matched.value)
    }

    @Test
    fun `resolves a numeric slot from eastern arabic-indic digits`() {
        val matched = VehicleCommandRouter.match("اضبط درجة الحرارة على ٢٢", "ar")
        assertNotNull(matched)
        assertEquals("ac.temperature", matched!!.command.id)
        assertEquals(22, matched.value)
    }

    @Test
    fun `numeric slot clamps to the command's valid range`() {
        val matched = VehicleCommandRouter.match("set the temperature to 99", "en")
        assertNotNull(matched)
        assertEquals(33, matched!!.value) // ac.temperature range is 17..33
    }

    @Test
    fun `unrelated speech does not match any vehicle command`() {
        assertNull(VehicleCommandRouter.match("what's the weather like today", "en"))
    }

    @Test
    fun `auto language tries both english and arabic`() {
        assertNotNull(VehicleCommandRouter.match("open the driver window", null))
        assertNotNull(VehicleCommandRouter.match("افتح نافذة السائق", null))
    }

    @Test
    fun `never resolves to a blocked-domain command`() {
        // Sanity check on the composed pipeline: even if intent data were ever mistakenly
        // authored against a blocked domain, the router must not return it.
        val allMatches = listOf("open the driver window", "turn on the ac", "open the trunk")
            .mapNotNull { VehicleCommandRouter.match(it, "en") }
        allMatches.forEach { assertEquals(false, it.command.domain.isBlocked) }
    }
}
