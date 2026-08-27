package com.kangrio.byd.assistant.vehicle

/**
 * Known BYD vehicle controls.
 *
 * **This table is unverified** — it's compiled from third-party reverse-engineering research
 * (hex feature-IDs for the "generic route", named-method signatures for a few typed setters),
 * not personally confirmed on real hardware. Treat every value here as a hypothesis to validate
 * once [ReflectionVehicleController] is wired up against a real DiLink unit, not as ground truth.
 *
 * Scope is a denylist, not an allowlist: anything not in [VehicleDomain]'s blocked set is fair
 * game to add here as new commands are discovered — see [VehicleDomain] for the hard-blocked
 * (structurally unreachable) domains.
 */
object VehicleCommandRegistry {

    val known: List<VehicleCommand> = listOf(
        // ── Climate (service 1000) ──────────────────────────────────────────────
        VehicleCommand(
            id = "ac.power",
            domain = VehicleDomain.CLIMATE,
            deviceType = 1000,
            featureId = 0x1DE00024,
            displayName = "A/C power",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.FixedEnum(mapOf("on" to 1, "off" to 0)),
        ),
        VehicleCommand(
            id = "ac.temperature",
            domain = VehicleDomain.CLIMATE,
            deviceType = 1000,
            featureId = 0x1DE00028,
            displayName = "A/C temperature",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.Range(17, 33, unit = "°C"),
        ),
        VehicleCommand(
            id = "ac.fan",
            domain = VehicleDomain.CLIMATE,
            deviceType = 1000,
            featureId = 0x1DE0000C,
            displayName = "A/C fan level",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.Range(0, 7),
        ),

        // ── Bodywork (service 1001) ──────────────────────────────────────────────
        VehicleCommand(
            id = "window.driver",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x43100038,
            displayName = "Driver window",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = WINDOW_CTRL_ENUM,
        ),
        VehicleCommand(
            id = "window.passenger",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x4310003B,
            displayName = "Passenger window",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = WINDOW_CTRL_ENUM,
        ),
        VehicleCommand(
            id = "window.rearLeft",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x43100040,
            displayName = "Rear-left window",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = WINDOW_CTRL_ENUM,
        ),
        VehicleCommand(
            id = "window.rearRight",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x43100043,
            displayName = "Rear-right window",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = WINDOW_CTRL_ENUM,
        ),
        VehicleCommand(
            id = "sunroof",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x43100008,
            displayName = "Sunroof",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.FixedEnum(mapOf("close" to 0, "open" to 252, "breath" to 253)),
        ),
        VehicleCommand(
            id = "sunroof.openPercent",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x4F500020,
            displayName = "Sunroof open percent",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.Range(0, 100, unit = "%"),
        ),
        VehicleCommand(
            id = "sunshade.percent",
            domain = VehicleDomain.BODYWORK,
            deviceType = 1001,
            featureId = 0x4F500028,
            displayName = "Sunshade",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.Range(0, 100, unit = "%"),
        ),

        // ── Audio (service 1002) ─────────────────────────────────────────────────
        VehicleCommand(
            id = "audio.volume",
            domain = VehicleDomain.AUDIO,
            deviceType = 1002,
            featureId = 0x4E061010,
            displayName = "Volume",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.Range(0, 30),
        ),

        // ── Setting (service 1023) — trunk uses the generic route; the rest are named setters ──
        VehicleCommand(
            id = "trunk",
            domain = VehicleDomain.SETTING,
            deviceType = 1023,
            featureId = 0x43100020,
            displayName = "Trunk",
            invocation = VehicleInvocation.GenericFeatureSet,
            parameter = VehicleParameter.FixedEnum(mapOf("open" to 1, "stop" to 2, "close" to 3)),
        ),
        VehicleCommand(
            id = "screen.rotation",
            domain = VehicleDomain.SETTING,
            invocation = VehicleInvocation.NamedMethod(
                deviceClass = "setting",
                methodName = "setPadRotation",
                paramTypes = listOf("int"),
                argsTemplate = listOf(null),
            ),
            displayName = "Screen rotation",
            parameter = VehicleParameter.FixedEnum(mapOf("horizontal" to 1, "vertical" to 2)),
        ),
        VehicleCommand(
            id = "light.ambientBrightness",
            domain = VehicleDomain.SETTING,
            invocation = VehicleInvocation.NamedMethod(
                deviceClass = "setting",
                methodName = "setIALBrightness",
                paramTypes = listOf("int", "int", "int"), // area, level, source
                argsTemplate = listOf(1, null, 1),         // area=1, source=1 fixed; level varies
            ),
            displayName = "Ambient light brightness",
            parameter = VehicleParameter.Range(1, 3),
        ),
        VehicleCommand(
            id = "seat.heating.driver",
            domain = VehicleDomain.SETTING,
            invocation = VehicleInvocation.NamedMethod(
                deviceClass = "setting",
                methodName = "setSeatHeatingState1",
                paramTypes = listOf("int", "int"),
                argsTemplate = listOf(1, null), // seat=1 (driver)
            ),
            displayName = "Driver seat heating",
            parameter = SEAT_STATE_ENUM,
        ),
        VehicleCommand(
            id = "seat.heating.passenger",
            domain = VehicleDomain.SETTING,
            invocation = VehicleInvocation.NamedMethod(
                deviceClass = "setting",
                methodName = "setSeatHeatingState1",
                paramTypes = listOf("int", "int"),
                argsTemplate = listOf(2, null), // seat=2 (passenger)
            ),
            displayName = "Passenger seat heating",
            parameter = SEAT_STATE_ENUM,
        ),
        VehicleCommand(
            id = "seat.ventilation.driver",
            domain = VehicleDomain.SETTING,
            invocation = VehicleInvocation.NamedMethod(
                deviceClass = "setting",
                methodName = "setSeatVentilatingState",
                paramTypes = listOf("int", "int"),
                argsTemplate = listOf(1, null),
            ),
            displayName = "Driver seat ventilation",
            parameter = SEAT_STATE_ENUM,
        ),
        VehicleCommand(
            id = "seat.ventilation.passenger",
            domain = VehicleDomain.SETTING,
            invocation = VehicleInvocation.NamedMethod(
                deviceClass = "setting",
                methodName = "setSeatVentilatingState",
                paramTypes = listOf("int", "int"),
                argsTemplate = listOf(2, null),
            ),
            displayName = "Passenger seat ventilation",
            parameter = SEAT_STATE_ENUM,
        ),
    )

    fun byId(id: String): VehicleCommand? = known.find { it.id == id }

    init {
        check(known.none { it.domain.isBlocked }) {
            "Registry must not contain blocked-domain commands: " +
                known.filter { it.domain.isBlocked }.map { it.id }
        }
        check(known.map { it.id }.distinct().size == known.size) {
            "Registry contains duplicate command ids"
        }
    }
}

private val WINDOW_CTRL_ENUM = VehicleParameter.FixedEnum(
    mapOf("open" to 1, "close" to 2, "stop" to 3, "half" to 4, "breath" to 5)
)

private val SEAT_STATE_ENUM = VehicleParameter.FixedEnum(mapOf("off" to 1, "on" to 2))
