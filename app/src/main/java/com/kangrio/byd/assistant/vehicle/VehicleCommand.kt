package com.kangrio.byd.assistant.vehicle

/**
 * A single known vehicle control. [deviceType]/[featureId] follow the BYD HAL's "generic feature
 * route" (`AbsBYDAutoDevice.set(deviceType, int[]{featureId}, int[]{value})`) and are only
 * meaningful when [invocation] is [VehicleInvocation.GenericFeatureSet] — named-method commands
 * don't have a hex feature code, so both are null there. [invocation] records which real HAL call
 * shape would actually be used once Phase 2 (see [ReflectionVehicleController]) is built, so this
 * data model doesn't need to change once that's implemented.
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
}
