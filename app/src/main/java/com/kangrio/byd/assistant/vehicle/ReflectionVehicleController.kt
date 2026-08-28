package com.kangrio.byd.assistant.vehicle

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.kangrio.byd.assistant.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException

/**
 * **Experimental — only wired in as the real dispatcher when the user explicitly arms it** (see
 * the "Enable experimental vehicle control" setting). Nothing here has been confirmed to actually
 * move anything in a real car yet; this makes no root/process-fork assumptions, matching the
 * user's own observation that other real apps invoke these controls without root.
 *
 * Two independent, evidenced invocation shapes are attempted, matching how a real, shipped BYD
 * DiLink app (i99dash) does it:
 *  - [VehicleInvocation.GenericFeatureSet]/[VehicleInvocation.NamedMethod] — reflection onto the
 *    `android.hardware.bydauto.<device>.BYDAuto<Device>Device` HAL classes.
 *  - [VehicleInvocation.AcBinderProperty] — a raw Binder transaction against a named sub-service
 *    of the `"byd_airconditioning"` system service, confirmed byte-for-byte from i99dash's own AC
 *    control code. For CLIMATE commands, the generic route is tried first (cheap, and its HAL
 *    class might exist under a name not yet found), falling back to this confirmed mechanism.
 *
 * No getter/read-back exists anywhere in the source research: [VehicleDispatchResult.Success]
 * here only ever means "the call did not throw," never "the car actually moved."
 */
class ReflectionVehicleController(private val context: Context) : VehicleController {
    private val instanceCache = HashMap<String, Any?>()

    override suspend fun dispatch(command: VehicleCommand, value: Int): VehicleDispatchResult {
        VehicleSafety.assertDispatchAllowed(command)

        val result = withContext(Dispatchers.IO) {
            when (val invocation = command.invocation) {
                is VehicleInvocation.NamedMethod -> tryNamedMethod(invocation, value)
                VehicleInvocation.GenericFeatureSet -> tryGenericSet(command, value)
                is VehicleInvocation.AcBinderProperty -> {
                    val generic = tryGenericSet(command, value)
                    if (generic is VehicleDispatchResult.Success) generic
                    else tryAcBinderProperty(invocation, command, value)
                }
            }
        }

        if (result is VehicleDispatchResult.Failure && result.error == VehicleDispatchError.SECURITY_DENIED) {
            return retryAfterPermissionGrant(command, value, result)
        }
        return result
    }

    /** On a `SecurityException`, its message typically names the missing permission — try to grant it via the
     * existing local-ADB mechanism and retry once. Still just as unconfirmed as everything else here;
     * this only reduces one specific, previously-anticipated failure mode to a single retry.
     *
     * Uses a short ADB timeout, not the usual 20s: this runs synchronously in the middle of a live
     * voice command with no "connecting…" UI of its own, so a dead/unreachable ADB connection must
     * fail fast rather than silently stalling the whole turn (the user just hears nothing happen). */
    private suspend fun retryAfterPermissionGrant(
        command: VehicleCommand,
        value: Int,
        original: VehicleDispatchResult.Failure,
    ): VehicleDispatchResult {
        val permission = original.detail?.let { PERMISSION_NAME_REGEX.find(it)?.value } ?: return original
        Log.i(TAG, "Security denied for ${command.id}, attempting to grant $permission via ADB and retrying")
        Utils.adbRequestPermission(context, permission, timeoutMs = LIVE_DISPATCH_ADB_TIMEOUT_MS)
        instanceCache.clear()
        return withContext(Dispatchers.IO) {
            when (val invocation = command.invocation) {
                is VehicleInvocation.NamedMethod -> tryNamedMethod(invocation, value)
                VehicleInvocation.GenericFeatureSet -> tryGenericSet(command, value)
                is VehicleInvocation.AcBinderProperty -> tryAcBinderProperty(invocation, command, value)
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
        // CLIMATE deliberately has no entry here — the guessed BYDAutoAcDevice class has zero
        // evidence of existing, and a real shipped app uses AcBinderProperty for AC instead.
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

    /**
     * The confirmed AC mechanism (from i99dash's `smali/a/g0.1.smali`): resolve the
     * `"byd_airconditioning"` service via the hidden `ServiceManager.getService()` API (no root —
     * same hidden-but-unprotected-API category as [tryGenericSet]), resolve a named sub-binder via
     * a `getSub(subServiceKey)` transaction (code 3) on the master binder, then issue a generic
     * `(id, area, value)` property-set transaction (also code 3) on that sub-binder. `id` is
     * [VehicleCommand.featureId] — the same numbering scheme is reused across the "set" and the
     * generic-route ID space, not a per-control transaction code.
     */
    private fun tryAcBinderProperty(
        invocation: VehicleInvocation.AcBinderProperty,
        command: VehicleCommand,
        value: Int,
    ): VehicleDispatchResult {
        val id = command.featureId
            ?: return VehicleDispatchResult.Failure(VehicleDispatchError.INVALID_ARGUMENT, "Missing featureId for ${command.id}")

        return try {
            val master = getAcServiceBinder() ?: return VehicleDispatchResult.Failure(
                VehicleDispatchError.CLASS_NOT_FOUND, "System service '$AC_SERVICE_NAME' not found"
            )
            val subBinder = getSubBinder(master, invocation.subServiceKey) ?: return VehicleDispatchResult.Failure(
                VehicleDispatchError.SUB_SERVICE_NOT_FOUND, "Sub-service '${invocation.subServiceKey}' not resolved"
            )
            setAcProperty(subBinder, invocation.interfaceDescriptor, id, invocation.area, value)
            VehicleDispatchResult.Success()
        } catch (e: SecurityException) {
            Log.w(TAG, "Security denied dispatching ${command.id} via AcBinderProperty", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.SECURITY_DENIED, e.message)
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error dispatching ${command.id} via AcBinderProperty", e)
            VehicleDispatchResult.Failure(VehicleDispatchError.UNKNOWN, e.message)
        }
    }

    /** `android.os.ServiceManager` is `@hide` (not root-gated) — reflection is the only way in. */
    private fun getAcServiceBinder(): IBinder? {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        return serviceManagerClass.getMethod("getService", String::class.java)
            .invoke(null, AC_SERVICE_NAME) as? IBinder
    }

    private fun getSubBinder(master: IBinder, subServiceKey: String): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(AC_MASTER_INTERFACE_DESCRIPTOR)
            data.writeString(subServiceKey)
            master.transact(TRANSACT_GET_SUB, data, reply, 0)
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Confirmed Parcel shape for a "set" call, shared across every `com.byd.ac.*` sub-interface:
     * `writeInterfaceToken, writeInt(count=1), writeInt(id), writeInt(area), writeString(typeName), writeValue(boxed)`. */
    private fun setAcProperty(subBinder: IBinder, interfaceDescriptor: String, id: Int, area: Int, value: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(interfaceDescriptor)
            data.writeInt(1)
            data.writeInt(id)
            data.writeInt(area)
            data.writeString("java.lang.Integer")
            data.writeValue(value)
            subBinder.transact(TRANSACT_SET_PROPERTY, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
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

        private const val AC_SERVICE_NAME = "byd_airconditioning"

        // Captured from an earlier research pass on the same source (i99dash's g0.1.smali) — not
        // reconfirmed in the final follow-up pass. Verify this literal against the class's
        // top-of-file interface-token constants before relying on it against real hardware.
        private const val AC_MASTER_INTERFACE_DESCRIPTOR = "com.byd.ac.IBydAcService"

        private const val TRANSACT_GET_SUB = 3
        private const val TRANSACT_SET_PROPERTY = 3

        private val PERMISSION_NAME_REGEX = Regex("[a-zA-Z_]+\\.permission\\.[A-Z_]+")
        private const val LIVE_DISPATCH_ADB_TIMEOUT_MS = 3_000L

        private val GENERIC_ROUTE_CLASS_BY_DOMAIN = mapOf(
            VehicleDomain.BODYWORK to "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
            VehicleDomain.AUDIO to "android.hardware.bydauto.audio.BYDAutoAudioDevice",
            VehicleDomain.SETTING to "android.hardware.bydauto.setting.BYDAutoSettingDevice",
        )
    }
}
