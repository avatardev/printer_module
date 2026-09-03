package com.avatarsolution.printer_module

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.MethodChannel.Result
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Permission state lives on the main thread; printer sessions live on the I/O executor. */
internal class UsbReadinessManager(
    private val context: Context,
    private val main: Handler,
    private val io: Executor,
    private val printer: PrinterHelper,
) {
    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val generations = ConcurrentHashMap<String, Long>()
    @Volatile private var closed = false
    private var pending: Request? = null
    val isPreparing: Boolean get() = pending != null
    // Accessed only on the printer executor, including by the plugin's legacy APIs.
    private var connected: DeviceSession? = null

    private data class DeviceSession(val device: UsbDevice, val generation: Long)
    private class Request(
        val session: DeviceSession,
        val result: Result,
        val permissionOnly: Boolean,
    ) {
        var receiver: BroadcastReceiver? = null
        var intent: PendingIntent? = null
        var timeout: Runnable? = null
    }

    private val attachmentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed) return
            val device = deviceExtra(intent) ?: return
            if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED &&
                intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            generations.compute(device.deviceName) { _, value -> (value ?: 0L) + 1L }
            pending?.takeIf { it.session.device.deviceName == device.deviceName }?.let {
                finish(it, "disconnected", "Printer USB terputus atau berubah. Coba cetak lagi.")
            }
            // Do not reconnect or show permission dialogs in the background. The next
            // ensureUsbReady call resolves the saved identity to the new device.
            io.execute { invalidateStaleConnection() }
        }
    }

    init {
        ContextCompat.registerReceiver(context, attachmentReceiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun ensure(identity: String, result: Result, permissionOnly: Boolean = false) {
        if (closed) {
            reply(result, permissionOnly, "unavailable", "Modul printer sudah ditutup.")
            return
        }
        if (pending != null) {
            reply(result, permissionOnly, "busy", "Permintaan USB lain masih diproses.")
            return
        }
        if (identity.isBlank()) {
            reply(result, permissionOnly, "invalidIdentity", "Identity printer USB kosong.")
            return
        }
        val device = try {
            UsbDeviceResolver.findDevice(usb, identity)
        } catch (e: Exception) {
            reply(result, permissionOnly, "invalidIdentity", e.message ?: "Identity USB tidak valid.")
            return
        }
        if (device == null) {
            io.execute { invalidateStaleConnection() }
            reply(result, permissionOnly, "notFound", "Printer USB belum terhubung atau belum menyala.")
            return
        }
        val request = Request(snapshot(device), result, permissionOnly)
        pending = request
        try {
            if (usb.hasPermission(device)) {
                open(request)
                return
            }
            val action = "${context.packageName}.PRINTER_USB_PERMISSION.${UUID.randomUUID()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != action || pending !== request) return
                    val returnedDevice = deviceExtra(intent) ?: return
                    if (returnedDevice.deviceName != device.deviceName ||
                        returnedDevice.deviceId != device.deviceId) return
                    clearPermission(request)
                    if (!isPresent(request.session)) {
                        finish(request, "disconnected", "Printer terputus saat menunggu izin.")
                    } else if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ||
                        !usb.hasPermission(device)) {
                        finish(request, "permissionDenied", "Izin akses printer USB ditolak.")
                    } else {
                        open(request)
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            request.receiver = receiver
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            request.intent = PendingIntent.getBroadcast(context, 0,
                Intent(action).setPackage(context.packageName), flags)
            request.timeout = Runnable {
                finish(request, "permissionTimeout", "Permintaan izin USB kedaluwarsa. Coba cetak lagi.")
            }.also { main.postDelayed(it, 60_000) }
            usb.requestPermission(device, request.intent!!)
        } catch (e: Exception) {
            finish(request, "connectionFailed", e.message ?: "Tidak dapat menyiapkan printer USB.")
        }
    }

    private fun open(request: Request) {
        if (request.permissionOnly) {
            finish(request, "ready", "Izin akses USB tersedia.")
            return
        }
        io.execute {
            var status = "connectionFailed"
            var message = "Koneksi printer USB gagal."
            var code: Int? = null
            try {
                if (!isPresent(request.session) || !usb.hasPermission(request.session.device)) {
                    status = "disconnected"
                    message = "Printer USB terputus sebelum koneksi dibuka."
                } else {
                    // Reopen explicitly instead of trusting the SDK's cached isConnect flag.
                    releaseConnection(force = true)
                    code = printer.connectUsbPrinter(request.session.device.deviceName)
                    if (!isPresent(request.session) || !usb.hasPermission(request.session.device)) {
                        releaseConnection(force = true)
                        status = "disconnected"
                        message = "Printer USB terputus saat koneksi dibuka."
                    } else if (code == 0 && printer.getStatus() == 1) {
                        connected = request.session
                        status = "ready"
                        message = "Printer USB siap digunakan."
                    } else {
                        releaseConnection(force = true)
                    }
                }
            } catch (e: Exception) {
                releaseConnection(force = true)
                message = e.message ?: message
            }
            main.post { finish(request, status, message, code) }
        }
    }

    private fun snapshot(device: UsbDevice) =
        DeviceSession(device, generations[device.deviceName] ?: 0L)

    private fun isPresent(session: DeviceSession): Boolean {
        if (closed || session.generation != (generations[session.device.deviceName] ?: 0L)) return false
        val current = usb.deviceList[session.device.deviceName] ?: return false
        return current.deviceId == session.device.deviceId &&
            current.vendorId == session.device.vendorId && current.productId == session.device.productId
    }

    /** Called only from the shared printer executor. */
    fun invalidateStaleConnection(): Boolean {
        val session = connected ?: return false
        if (isPresent(session) && usb.hasPermission(session.device)) return false
        releaseConnection()
        return true
    }

    /** Legacy explicit connections also participate in detach cleanup. I/O thread only. */
    fun connectLegacy(identity: String): Int {
        releaseConnection()
        val device = UsbDeviceResolver.findDevice(usb, identity) ?: return -1
        val session = snapshot(device)
        val code = printer.connectUsbPrinter(device.deviceName)
        if (code == 0 && isPresent(session)) connected = session
        else releaseConnection(force = true)
        return if (isPresent(session)) code else -1
    }

    /** Switching to LAN/serial must stop USB events from closing that connection. */
    fun releaseConnection(force: Boolean = false) {
        val hadConnection = connected != null
        connected = null
        if (hadConnection || force) {
            try { printer.deInitPrinter() }
            catch (e: Exception) { Log.w("PrinterModule", "USB disconnect failed", e) }
        }
    }

    private fun clearPermission(request: Request) {
        request.timeout?.let { main.removeCallbacks(it) }
        request.timeout = null
        request.receiver?.let { context.unregisterReceiver(it) }
        request.receiver = null
        request.intent?.cancel()
        request.intent = null
    }

    private fun finish(request: Request, status: String, message: String, code: Int? = null) {
        if (pending !== request) return
        pending = null
        clearPermission(request)
        reply(request.result, request.permissionOnly, status, message, code)
    }

    private fun reply(result: Result, permissionOnly: Boolean, status: String, message: String, code: Int? = null) {
        if (permissionOnly) {
            when (status) {
                "ready" -> result.success(true)
                "permissionDenied" -> result.success(false)
                "notFound" -> result.error("USB_NOT_FOUND", message, null)
                "invalidIdentity" -> result.error("INVALID_USB", message, null)
                else -> result.error("USB_${status.uppercase()}", message, null)
            }
        } else {
            result.success(mapOf("status" to status, "message" to message, "connectionCode" to code))
        }
    }

    fun close() {
        closed = true
        pending?.let { finish(it, "unavailable", "Modul printer sudah ditutup.") }
        context.unregisterReceiver(attachmentReceiver)
        io.execute { releaseConnection() }
    }

    @Suppress("DEPRECATION")
    private fun deviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
