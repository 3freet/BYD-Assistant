package com.kangrio.byd.assistant.vehicle

import com.kangrio.byd.assistant.vehicle.intent.VehicleIntentSpec
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

/**
 * Unit-level coverage for the DTO -> sealed-type mapping in [VehicleCommandRegistry.kt], isolated
 * from the real `vehicle_commands.json` asset: an unrecognized "type"/"domain" discriminator must
 * fail loudly (fail closed), never silently default to something permissive.
 */
class VehicleCommandJsonLoaderTest {

    @Test
    fun `unknown domain fails to load rather than defaulting`() {
        val dto = CommandEntryDto(
            id = "test.command",
            domain = "NOT_A_REAL_DOMAIN",
            displayName = "Test",
            invocation = InvocationDto(type = "genericFeatureSet"),
            parameter = ParameterDto(type = "range", min = 0, max = 1),
        )
        assertThrows(IllegalStateException::class.java) { dto.toVehicleCommand() }
    }

    @Test
    fun `unknown invocation type fails to load rather than defaulting`() {
        val dto = CommandEntryDto(
            id = "test.command",
            domain = "CLIMATE",
            displayName = "Test",
            invocation = InvocationDto(type = "not_a_real_invocation"),
            parameter = ParameterDto(type = "range", min = 0, max = 1),
        )
        assertThrows(IllegalStateException::class.java) { dto.toVehicleCommand() }
    }

    @Test
    fun `unknown parameter type fails to load rather than defaulting`() {
        val dto = CommandEntryDto(
            id = "test.command",
            domain = "CLIMATE",
            displayName = "Test",
            invocation = InvocationDto(type = "genericFeatureSet"),
            parameter = ParameterDto(type = "not_a_real_parameter"),
        )
        assertThrows(IllegalStateException::class.java) { dto.toVehicleCommand() }
    }

    @Test
    fun `acBinderProperty invocation missing required fields fails to load`() {
        val dto = CommandEntryDto(
            id = "test.command",
            domain = "CLIMATE",
            displayName = "Test",
            invocation = InvocationDto(type = "acBinderProperty"), // missing subServiceKey/interfaceDescriptor
            parameter = ParameterDto(type = "range", min = 0, max = 1),
        )
        assertThrows(IllegalStateException::class.java) { dto.toVehicleCommand() }
    }

    @Test
    fun `a well-formed entry round-trips into the expected sealed types`() {
        val dto = CommandEntryDto(
            id = "test.acBinder",
            domain = "CLIMATE",
            displayName = "Test AC control",
            featureId = 42,
            invocation = InvocationDto(
                type = "acBinderProperty",
                subServiceKey = "AC_AIRCONDITIONER_SERVICE",
                interfaceDescriptor = "com.byd.ac.IAcAirConditioner",
            ),
            parameter = ParameterDto(type = "range", min = 0, max = 3),
            intents = listOf(
                IntentDto(
                    type = "numericSlot",
                    phrases = mapOf("en" to listOf("set test to")),
                    confirmation = mapOf("en" to "Setting test to %d."),
                )
            ),
        )

        val command = dto.toVehicleCommand()
        assertEquals(VehicleDomain.CLIMATE, command.domain)
        assertEquals(42, command.featureId)
        val invocation = command.invocation as VehicleInvocation.AcBinderProperty
        assertEquals("AC_AIRCONDITIONER_SERVICE", invocation.subServiceKey)
        assertEquals("com.byd.ac.IAcAirConditioner", invocation.interfaceDescriptor)
        assertEquals(0, invocation.area) // defaults to 0 when omitted

        val range = command.parameter as VehicleParameter.Range
        assertEquals(0, range.min)
        assertEquals(3, range.max)

        val specs = dto.toIntentSpecs()
        assertTrue(specs.single() is VehicleIntentSpec.NumericSlot)
        assertEquals("test.acBinder", specs.single().commandId)
    }
}
