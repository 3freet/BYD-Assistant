package com.kangrio.byd.assistant.vehicle

/**
 * A single known vehicle control. [deviceType]/[featureId] follow the BYD HAL's "generic feature
 * route" (`AbsBYDAutoDevice.set(deviceType, int[]{featureId}, int[]{value})`) and are used when
 * [invocation] is [VehicleInvocation.GenericFeatureSet]. [featureId] is also reused as the
 * property `id` argument when [invocation] is [VehicleInvocation.AcBinderProperty] — the same
 * BYD signal-numbering scheme shows up in both invocation surfaces. [NamedMethod][VehicleInvocation.NamedMethod]
 * commands don't have a hex feature code, so both are null there.
 */
data class VehicleCommand(
    val id: String,
    val domain: VehicleDomain,
    val deviceType: Int? = null,
    val featureId: Int? = null,
    val displayName: String,
    val invocation: VehicleInvocation,
    val parameter: VehicleParameter,
)

sealed interface VehicleParameter {
    /** e.g. open/close/stop/half/breath -> 1/2/3/4/5. */
    data class FixedEnum(val options: Map<String, Int>) : VehicleParameter

    /** e.g. AC temperature 17..33 °C. */
    data class Range(val min: Int, val max: Int, val unit: String? = null) : VehicleParameter
}

sealed interface VehicleInvocation {
    /** `AbsBYDAutoDevice.set(deviceType, int[]{featureId}, int[]{value})` — documented as `protected`. */
    data object GenericFeatureSet : VehicleInvocation

    /**
     * A named typed setter on a device singleton, e.g. `setSeatHeatingState1(seat, level)`.
     * [paramTypes] is the reflection signature (e.g. `["int", "int"]`) used to look up the method.
     * [argsTemplate] is the actual call arguments with exactly one `null` marking the position the
     * dispatch-time value fills — e.g. `[1, null]` calls `method(1, value)` (seat=driver fixed,
     * level=variable), and `[1, null, 1]` calls `method(1, value, 1)` for a 3-arg setter where
     * only the middle argument varies.
     */
    data class NamedMethod(
        val deviceClass: String,
        val methodName: String,
        val paramTypes: List<String>,
        val argsTemplate: List<Int?>,
    ) : VehicleInvocation {
        init {
            require(argsTemplate.count { it == null } == 1) {
                "argsTemplate must have exactly one null (the dispatch-time value slot): $argsTemplate"
            }
            require(paramTypes.size == argsTemplate.size) {
                "paramTypes and argsTemplate must be the same length"
            }
        }
    }

    /**
     * The confirmed real mechanism for AC/climate controls, reverse-engineered from a real
     * shipped BYD DiLink app (i99dash): resolve `com.byd.ac.<interfaceDescriptor>` as a named
     * sub-binder of the `"byd_airconditioning"` system service (via a `getSub(subServiceKey)`
     * Binder call on the master service), then issue a generic `(id, area, value)` property-set
     * transaction on that sub-binder — `id` is the owning [VehicleCommand.featureId], not a
     * separate transaction code per control. See [ReflectionVehicleController] for the transact
     * codes and Parcel shape.
     */
    data class AcBinderProperty(
        val subServiceKey: String,
        val interfaceDescriptor: String,
        val area: Int = 0,
    ) : VehicleInvocation
}
