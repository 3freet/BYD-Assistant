package com.kangrio.byd.assistant.vehicle

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class VehicleCommandRegistryTest {

    @Test
    fun `registry has no blocked-domain commands`() {
        // Merely loading VehicleCommandRegistry already runs its init{} check that would throw
        // on a blocked-domain entry; this re-asserts the invariant explicitly for clarity.
        assertTrue(VehicleCommandRegistry.known.none { it.domain.isBlocked })
    }

    @Test
    fun `registry has no duplicate ids`() {
        val ids = VehicleCommandRegistry.known.map { it.id }
        assertTrue(ids.distinct().size == ids.size)
    }

    @Test
    fun `byId finds a known command`() {
        assertNotNull(VehicleCommandRegistry.byId("ac.power"))
        assertNotNull(VehicleCommandRegistry.byId("window.driver"))
    }

    @Test
    fun `byId returns null for an unknown id`() {
        assertNull(VehicleCommandRegistry.byId("not.a.real.command"))
    }

    @Test
    fun `named-method commands have no hex feature id`() {
        val namedMethodCommands = VehicleCommandRegistry.known.filter { it.invocation is VehicleInvocation.NamedMethod }
        assertFalse(namedMethodCommands.isEmpty())
        namedMethodCommands.forEach {
            assertNull("${it.id} should not carry a generic-route featureId", it.featureId)
        }
    }
}
