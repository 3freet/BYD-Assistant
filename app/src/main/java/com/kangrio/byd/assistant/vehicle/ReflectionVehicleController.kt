package com.kangrio.byd.assistant.vehicle

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException

/**
 * **Phase 2 / experimental — not wired in as the default [VehicleController].**
 *
 * Attempts the actual BYD HAL call from this app's own process. Nothing in the source research
 * this was built from demonstrates a working invocation for either call shape below, and the
 * user has separately confirmed the documented "requires root" claim doesn't match what they've
 * observed from other real apps — so this makes no root/process-fork assumptions. Instead it
 * tries both plausible shapes and reports back a specific [VehicleDispatchError] so the real
 * mechanism (and whether any permission grant is needed — see [com.kangrio.byd.assistant.util.Utils.adbRequestPermission])
 * can be determined empirically on real hardware.
 *
 * No getter/read-back exists anywhere in the source research: [VehicleDispatchResult.Success]
 * here only ever means "the call did not throw," never "the car actually moved."
 */
class ReflectionVehicleController(private val context: Context) : VehicleController {
    private val instanceCache = HashMap<String, Any?>()

    override suspend fun dispatch(command: VehicleCommand, value: Int): VehicleDispatchResult {
        VehicleSafety.assertDispatchAllowed(command)

        return withContext(Dispatchers.IO) {
            when (val invocation = command.invocation) {
                is VehicleInvocation.NamedMethod -> tryNamedMethod(invocation, value)
                VehicleInvocation.GenericFeatureSet -> tryGenericSet(command, value)
            }
        }
    }

    private fun tryNamedMethod(invocation: VehicleInvocation.NamedMethod, value: Int): VehicleDispatchResult {
        val className = "android.hardware.bydauto.${invocation.deviceClass}.BYDAuto${invocation.deviceClass.replaceFirstChar { it.uppercase() }}Device"
        return try {
            val instance = getOrCreateInstance(className) ?: return VehicleDispatchResult.Failure(
                VehicleDispatchError.CLASS_NOT_FOUND, "No usable instance for $className"
            )
            val paramTypes = invocation.paramTypes.map { intClassFor(it) }.toTypedArray()
            val args = invocation.argsTemplate.map { it ?: value }.toTypedArray()

            val method = try {
                instance.javaClass.getMethod(invocation.methodName, *paramTypes)
            } catch (_: NoSuchMethodException) {
                instance.javaClass.getDeclaredMethod(invocation.methodName, *paramTypes).apply { isAccessible = true }
            }
            method.invoke(instance, *args)
            VehicleDispatchResult.Success()
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Class not found: $className", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.CLASS_NOT_FOUND, e.message)
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "Method not found: ${invocation.methodName} on $className", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.METHOD_NOT_FOUND, e.message)
        } catch (e: SecurityException) {
            Log.w(TAG, "Security denied calling ${invocation.methodName}", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.SECURITY_DENIED, e.message)
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "${invocation.methodName} threw", e.targetException)
            VehicleDispatchResult.Failure(VehicleDispatchError.INVOCATION_FAILED, e.targetException?.message)
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error calling ${invocation.methodName}", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.UNKNOWN, e.message)
        }
    }

    /** `AbsBYDAutoDevice.set(deviceType, int[]{featureId}, int[]{value})` — documented `protected`. */
    private fun tryGenericSet(command: VehicleCommand, value: Int): VehicleDispatchResult {
        val deviceType = command.deviceType
        val featureId = command.featureId
        if (deviceType == null || featureId == null) {
            return VehicleDispatchResult.Failure(VehicleDispatchError.INVALID_ARGUMENT, "Missing deviceType/featureId for ${command.id}")
        }

        // The generic route's owning device class isn't recorded per-command since it's shared
        // across a whole service group; resolve by domain instead of a hardcoded class guess.
        val className = GENERIC_ROUTE_CLASS_BY_DOMAIN[command.domain] ?: return VehicleDispatchResult.Failure(
            VehicleDispatchError.CLASS_NOT_FOUND, "No known HAL class for domain ${command.domain}"
        )

        return try {
            val instance = getOrCreateInstance(className) ?: return VehicleDispatchResult.Failure(
                VehicleDispatchError.CLASS_NOT_FOUND, "No usable instance for $className"
            )
            val method = try {
                instance.javaClass.getMethod("set", Int::class.java, IntArray::class.java, IntArray::class.java)
            } catch (_: NoSuchMethodException) {
                instance.javaClass.getDeclaredMethod("set", Int::class.java, IntArray::class.java, IntArray::class.java)
                    .apply { isAccessible = true }
            }
            method.invoke(instance, deviceType, intArrayOf(featureId), intArrayOf(value))
            VehicleDispatchResult.Success()
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Class not found: $className", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.CLASS_NOT_FOUND, e.message)
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "set() not found on $className", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.METHOD_NOT_FOUND, e.message)
        } catch (e: SecurityException) {
            Log.w(TAG, "Security denied calling set() on $className", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.SECURITY_DENIED, e.message)
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "set() threw on $className", e.targetException)
            VehicleDispatchResult.Failure(VehicleDispatchError.INVOCATION_FAILED, e.targetException?.message)
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error calling set() on $className", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.UNKNOWN, e.message)
        }
    }

    /** Tries a static `getInstance(Context)` first, falling back to a no-arg constructor. */
    private fun getOrCreateInstance(className: String): Any? = instanceCache.getOrPut(className) {
        val clazz = Class.forName(className)
        try {
            clazz.getMethod("getInstance", Context::class.java).invoke(null, context)
        } catch (_: NoSuchMethodException) {
            try {
                clazz.getMethod("getInstance").invoke(null)
            } catch (_: NoSuchMethodException) {
                clazz.getDeclaredConstructor().newInstance()
            }
        }
    }

    private fun intClassFor(typeName: String): Class<*> = when (typeName) {
        "int" -> Int::class.javaPrimitiveType!!
        else -> Class.forName(typeName)
    }

    companion object {
        private const val TAG = "VehicleController"

        private val GENERIC_ROUTE_CLASS_BY_DOMAIN = mapOf(
            VehicleDomain.CLIMATE to "android.hardware.bydauto.ac.BYDAutoAcDevice",
            VehicleDomain.BODYWORK to "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
            VehicleDomain.AUDIO to "android.hardware.bydauto.audio.BYDAutoAudioDevice",
            VehicleDomain.SETTING to "android.hardware.bydauto.setting.BYDAutoSettingDevice",
        )
    }
}
