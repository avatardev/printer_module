package com.avatarsolution.printer_module

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.Locale

/**
 * Resolves a saved USB printer identity to the currently attached device.
 *
 * The kernel assigns the device path (/dev/bus/usb/001/002) when the device
 * is plugged in and increments it on every reconnect, so a saved path goes
 * stale. "vid:pid" (hex, optionally suffixed with ":serial") is the stable
 * identity that survives reconnects; the raw path is still accepted so saved
 * paths keep working while they are valid.
 */
internal object UsbDeviceResolver {
    fun findDevice(usbManager: UsbManager, identity: String): UsbDevice? {
        usbManager.deviceList[identity]?.let { return it }

        val parts = identity.trim().split(":")
        if (parts.size < 2) return null
        val vid = parts[0].toIntOrNull(16) ?: return null
        val pid = parts[1].toIntOrNull(16) ?: return null
        val serial = if (parts.size > 2) parts.drop(2).joinToString(":") else null

        // serialNumber is only readable once USB permission is granted
        // (Android 10+), so a serial-qualified identity will not match until
        // then and should be used only when devices of identical vid:pid
        // must be told apart.
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == vid &&
                device.productId == pid &&
                (serial == null || device.serialNumber == serial)
        }
    }

    /** Stable identity safe to persist across reconnects. */
    fun identityOf(device: UsbDevice): String =
        String.format(Locale.US, "%04x:%04x", device.vendorId, device.productId)
}
