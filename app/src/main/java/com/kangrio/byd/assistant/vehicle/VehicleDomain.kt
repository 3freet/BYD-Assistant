package com.kangrio.byd.assistant.vehicle

/**
 * Vehicle subsystem groupings from the BYD HAL research. `isBlocked` domains control how the
 * car drives or brakes — a misheard voice command there is a crash, not an inconvenience — and
 * must never be reachable, regardless of what any future command source (matcher, on-device LLM,
 * manual test tooling) tries to dispatch. [UNKNOWN] exists so anything that fails to resolve to a
 * real domain fails closed rather than open.
 */
enum class VehicleDomain(val isBlocked: Boolean) {
    CLIMATE(isBlocked = false),
    BODYWORK(isBlocked = false),
    AUDIO(isBlocked = false),
    LIGHT(isBlocked = false),
    SETTING(isBlocked = false),

    ADAS(isBlocked = true),
    MOTOR(isBlocked = true),
    ENGINE(isBlocked = true),
    GEARBOX(isBlocked = true),
    RADAR(isBlocked = true),
    SECURITY(isBlocked = true),
    POWER(isBlocked = true),

    UNKNOWN(isBlocked = true),
}
