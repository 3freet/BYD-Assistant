package com.kangrio.byd.assistant.vehicle

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kangrio.byd.assistant.vehicle.intent.VehicleIntentSpec
import java.io.InputStreamReader

private const val TAG = "VehicleCommandRegistry"

/**
 * Known BYD vehicle controls, loaded from `vehicle_commands.json` (bundled as a classpath
 * resource, not an Android asset, so the exact same file loads in plain JUnit tests and on a
 * real device) instead of hardcoded Kotlin literals — this is what lets a new command be added
 * without touching this file.
 *
 * Every numeric ID here was either cross-validated decimal-for-decimal against a real, shipped
 * BYD DiLink app's own extracted signal catalog, or newly derived from that same catalog — see
 * `now-i-have-a-steady-allen.md` for the research trail. That upgrades confidence in the
 * *numbers*, not in whether [ReflectionVehicleController]'s invocation mechanisms actually work
 * from this app's process — that's still an open, real-hardware question.
 *
 * Scope is a denylist, not an allowlist: anything not in [VehicleDomain]'s blocked set is fair
 * game to add as a new entry in the JSON — see [VehicleDomain] for the hard-blocked domains.
 *
 * Loading fails **per-entry, not for the whole registry**: this object is touched on every single
 * voice utterance (via [VehicleCommandRouter]), including ones with nothing to do with the car, so
 * one malformed hand-edit to the JSON must never brick the entire assistant. A bad/unrecognized
 * entry, a blocked-domain entry, or a duplicate id is dropped and logged loudly instead of thrown —
 * [VehicleCommandRouter] and [VehicleSafety] remain the hard, throwing gates at match/dispatch
 * time, so this relaxation doesn't weaken the actual safety floor.
 */
object VehicleCommandRegistry {
    private const val RESOURCE_NAME = "vehicle_commands.json"

    /** Package-internal so [VehicleIntentSpec] data can be derived from the same parsed file
     * without a second read; the parsed [VehicleCommand]s are the only public surface. */
    internal val entries: List<CommandEntryDto> = loadEntriesSafely()

    val known: List<VehicleCommand> = entries.toSafeVehicleCommands()

    fun byId(id: String): VehicleCommand? = known.find { it.id == id }

    private fun loadEntriesSafely(): List<CommandEntryDto> = try {
        val stream = javaClass.classLoader?.getResourceAsStream(RESOURCE_NAME)
            ?: error("$RESOURCE_NAME not found on the classpath")
        stream.use {
            InputStreamReader(it, Charsets.UTF_8).use { reader ->
                Gson().fromJson(reader, object : TypeToken<List<CommandEntryDto>>() {}.type)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load $RESOURCE_NAME — vehicle control is unavailable this session", e)
        emptyList()
    }
}

// ── JSON DTOs and mapping to the sealed domain types ────────────────────────────────────────
// Kept as loose/nullable DTOs (rather than Gson polymorphic deserializers onto the sealed types
// directly) so a malformed or unrecognized "type" discriminator is easy to catch per-entry below.

internal data class CommandEntryDto(
    val id: String,
    val domain: String,
    val displayName: String,
    val deviceType: Int? = null,
    val featureId: Int? = null,
    val invocation: InvocationDto,
    val parameter: ParameterDto,
    val intents: List<IntentDto> = emptyList(),
)

internal data class InvocationDto(
    val type: String,
    // namedMethod
    val deviceClass: String? = null,
    val methodName: String? = null,
    val paramTypes: List<String>? = null,
    val argsTemplate: List<Int?>? = null,
    // acBinderProperty
    val subServiceKey: String? = null,
    val interfaceDescriptor: String? = null,
    val area: Int? = null,
)

internal data class ParameterDto(
    val type: String,
    val options: Map<String, Int>? = null, // fixedEnum
    val min: Int? = null, // range
    val max: Int? = null,
    val unit: String? = null,
)

internal data class IntentDto(
    val type: String, // "fixedValue" | "numericSlot"
    val value: Int? = null, // fixedValue only
    val phrases: Map<String, List<String>> = emptyMap(),
    val confirmation: Map<String, String> = emptyMap(),
)

/** Maps every entry, dropping (and loudly logging) anything that fails to parse, names a blocked
 * domain, or duplicates an id already seen — never throws, so one bad entry can't take the whole
 * registry (and therefore the whole voice pipeline) down with it. */
internal fun List<CommandEntryDto>.toSafeVehicleCommands(): List<VehicleCommand> {
    val parsed = mapNotNull { entry ->
        try {
            entry.toVehicleCommand()
        } catch (e: Exception) {
            Log.e(TAG, "Dropping malformed vehicle command '${entry.id}': ${e.message}")
            null
        }
    }

    val (safe, blocked) = parsed.partition { !it.domain.isBlocked }
    if (blocked.isNotEmpty()) {
        Log.e(TAG, "Dropping blocked-domain commands that should never have been in the registry: ${blocked.map { it.id }}")
    }

    val seenIds = HashSet<String>()
    val deduped = safe.filter { seenIds.add(it.id) }
    if (deduped.size != safe.size) {
        Log.e(TAG, "Dropped duplicate command ids: ${safe.map { it.id }.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    return deduped
}

internal fun CommandEntryDto.toVehicleCommand(): VehicleCommand {
    val domainEnum = VehicleDomain.entries.find { it.name == domain }
        ?: error("Unknown vehicle domain '$domain' for command '$id' — refusing to load")
    return VehicleCommand(
        id = id,
        domain = domainEnum,
        deviceType = deviceType,
        featureId = featureId,
        displayName = displayName,
        invocation = invocation.toVehicleInvocation(id),
        parameter = parameter.toVehicleParameter(id),
    )
}

internal fun CommandEntryDto.toIntentSpecs(): List<VehicleIntentSpec> = intents.map { it.toVehicleIntentSpec(id) }

private fun InvocationDto.toVehicleInvocation(commandId: String): VehicleInvocation = when (type) {
    "genericFeatureSet" -> VehicleInvocation.GenericFeatureSet
    "namedMethod" -> VehicleInvocation.NamedMethod(
        deviceClass = deviceClass ?: error("namedMethod invocation for '$commandId' missing deviceClass"),
        methodName = methodName ?: error("namedMethod invocation for '$commandId' missing methodName"),
        paramTypes = paramTypes ?: error("namedMethod invocation for '$commandId' missing paramTypes"),
        argsTemplate = argsTemplate ?: error("namedMethod invocation for '$commandId' missing argsTemplate"),
    )
    "acBinderProperty" -> VehicleInvocation.AcBinderProperty(
        subServiceKey = subServiceKey ?: error("acBinderProperty invocation for '$commandId' missing subServiceKey"),
        interfaceDescriptor = interfaceDescriptor ?: error("acBinderProperty invocation for '$commandId' missing interfaceDescriptor"),
        area = area ?: 0,
    )
    else -> error("Unknown invocation type '$type' for command '$commandId' — refusing to load")
}

private fun ParameterDto.toVehicleParameter(commandId: String): VehicleParameter = when (type) {
    "fixedEnum" -> VehicleParameter.FixedEnum(options ?: error("fixedEnum parameter for '$commandId' missing options"))
    "range" -> VehicleParameter.Range(
        min = min ?: error("range parameter for '$commandId' missing min"),
        max = max ?: error("range parameter for '$commandId' missing max"),
        unit = unit,
    )
    else -> error("Unknown parameter type '$type' for command '$commandId' — refusing to load")
}

private fun IntentDto.toVehicleIntentSpec(commandId: String): VehicleIntentSpec = when (type) {
    "fixedValue" -> VehicleIntentSpec.FixedValue(
        commandId = commandId,
        value = value ?: error("fixedValue intent for '$commandId' missing value"),
        phrasesByLanguage = phrases,
        confirmationTemplateByLanguage = confirmation,
    )
    "numericSlot" -> VehicleIntentSpec.NumericSlot(
        commandId = commandId,
        phrasesByLanguage = phrases,
        confirmationTemplateByLanguage = confirmation,
    )
    else -> error("Unknown intent type '$type' for command '$commandId' — refusing to load")
}
