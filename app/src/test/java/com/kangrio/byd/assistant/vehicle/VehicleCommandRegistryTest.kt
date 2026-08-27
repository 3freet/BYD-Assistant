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

    @Test
    fun `seat heating and ventilation are 0 to 3 intensity levels, not binary on-off`() {
        val seatCommandIds = listOf(
            "seat.heating.driver", "seat.heating.passenger", "seat.heating.rearLeft", "seat.heating.rearRight",
            "seat.ventilation.driver", "seat.ventilation.passenger", "seat.ventilation.rearLeft", "seat.ventilation.rearRight",
        )
        seatCommandIds.forEach { id ->
            val command = VehicleCommandRegistry.byId(id)
            assertNotNull("$id should exist in the registry", command)
            val range = command!!.parameter as? VehicleParameter.Range
            assertNotNull("$id should have a Range parameter, not a fixed enum", range)
            assertTrue("$id should dispatch via the confirmed AC Binder mechanism", command.invocation is VehicleInvocation.AcBinderProperty)
            assertTrue(range!!.min == 0 && range.max == 3)
        }
    }

    @Test
    fun `ac windMode is a five-option fixed enum`() {
        val command = VehicleCommandRegistry.byId("ac.windMode")
        assertNotNull(command)
        val enum = command!!.parameter as? VehicleParameter.FixedEnum
        assertNotNull("ac.windMode should have a FixedEnum parameter", enum)
        assertTrue(enum!!.options.size == 5)
    }

    @Test
    fun `climate domain commands never resolve to the removed BYDAutoAcDevice guess`() {
        // Regression guard: CLIMATE must have no entry in ReflectionVehicleController's generic-route
        // class map, since the guessed class has zero evidence of existing — AcBinderProperty is the
        // confirmed mechanism instead. This can't inspect the private map directly, so it asserts the
        // proxy for that decision: every CLIMATE command with a numeric featureId is wired through
        // AcBinderProperty, not left as a bare GenericFeatureSet that would only ever hit that guess.
        VehicleCommandRegistry.known
            .filter { it.domain == VehicleDomain.CLIMATE && it.featureId != null }
            .forEach {
                assertTrue("${it.id} should use AcBinderProperty", it.invocation is VehicleInvocation.AcBinderProperty)
            }
    }
}
