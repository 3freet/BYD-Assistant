package com.kangrio.byd.assistant.vehicle

import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

class VehicleSafetyTest {

    private fun commandWith(domain: VehicleDomain) = VehicleCommand(
        id = "test.${domain.name.lowercase()}",
        domain = domain,
        displayName = "test",
        invocation = VehicleInvocation.GenericFeatureSet,
        parameter = VehicleParameter.Range(0, 1),
    )

    @Test
    fun `every blocked domain is refused`() {
        VehicleDomain.entries.filter { it.isBlocked }.forEach { domain ->
            assertThrows(IllegalStateException::class.java) {
                VehicleSafety.assertDispatchAllowed(commandWith(domain))
            }
        }
    }

    @Test
    fun `every non-blocked domain is allowed`() {
        VehicleDomain.entries.filterNot { it.isBlocked }.forEach { domain ->
            VehicleSafety.assertDispatchAllowed(commandWith(domain)) // must not throw
        }
    }

    @Test
    fun `at least one domain is blocked and one is not, so the test above is meaningful`() {
        assertTrue(VehicleDomain.entries.any { it.isBlocked })
        assertTrue(VehicleDomain.entries.any { !it.isBlocked })
    }
}
