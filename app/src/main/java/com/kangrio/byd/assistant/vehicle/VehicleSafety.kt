package com.kangrio.byd.assistant.vehicle

/**
 * The dispatch-time enforcement floor for blocked vehicle domains (adas/motor/engine/gearbox/
 * radar/security/power). Every [VehicleController] implementation MUST call
 * [assertDispatchAllowed] as its first statement — this is the one gate that also protects a
 * future manual/debug dispatch path that bypasses the matcher/router entirely.
 */
object VehicleSafety {
    fun assertDispatchAllowed(command: VehicleCommand) {
        check(!command.domain.isBlocked) {
            "Refusing to dispatch '${command.id}': domain ${command.domain} is hard-blocked"
        }
    }
}
