package com.kangrio.byd.assistant.vehicle.intent

import com.kangrio.byd.assistant.vehicle.VehicleCommandRegistry
import com.kangrio.byd.assistant.vehicle.toIntentSpecs

/**
 * Bilingual voice phrasings for the commands, loaded from the same `vehicle_commands.json` as
 * [VehicleCommandRegistry] — adding a command's phrasing is a JSON edit, not a Kotlin change.
 * Not every registry command has phrasing yet (e.g. sunroof open-percent, screen rotation) —
 * those are addressable but intentionally left with an empty `intents` list in the JSON.
 *
 * **Arabic phrases are a first draft, not reviewed by a native speaker** — expect to need real
 * refinement once tested against actual Arabic STT transcripts.
 */
object VehicleIntentSpecs {
    val all: List<VehicleIntentSpec> = VehicleCommandRegistry.entries.flatMap { it.toIntentSpecs() }
}
