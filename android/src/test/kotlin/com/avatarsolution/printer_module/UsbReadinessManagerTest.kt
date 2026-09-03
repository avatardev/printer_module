package com.avatarsolution.printer_module

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.mockito.Mockito.*

internal class UsbReadinessManagerTest {
    private fun device(path: String, id: Int): UsbDevice = mock(UsbDevice::class.java).also {
        `when`(it.deviceName).thenReturn(path)
        `when`(it.deviceId).thenReturn(id)
        `when`(it.vendorId).thenReturn(0x0483)
        `when`(it.productId).thenReturn(0x5743)
    }

    private class Fixture(val device: UsbDevice) : AutoCloseable {
        val context = mock(Context::class.java)
        val usb = mock(UsbManager::class.java)
        val handler = mock(Handler::class.java)
        val printer = mock(PrinterHelper::class.java)
        val devices = hashMapOf(device.deviceName to device)
        val receivers = mutableListOf<BroadcastReceiver>()
        var permission = true
        var action = ""
        var timeout: Runnable? = null
        private val compat = mockStatic(ContextCompat::class.java)
        private val pendingIntents = mockStatic(PendingIntent::class.java)
        private val intents = mockConstruction(Intent::class.java) { _, construction ->
            (construction.arguments().firstOrNull() as? String)?.let { action = it }
        }
        val manager: UsbReadinessManager

        init {
            `when`(context.getSystemService(Context.USB_SERVICE)).thenReturn(usb)
            `when`(context.packageName).thenReturn("test.printer")
            `when`(usb.deviceList).thenAnswer { devices }
            `when`(usb.hasPermission(any(UsbDevice::class.java))).thenAnswer { permission }
            `when`(printer.connectUsbPrinter(anyString())).thenReturn(0)
            `when`(printer.getStatus()).thenReturn(1)
            `when`(handler.post(any(Runnable::class.java))).thenAnswer {
                it.getArgument<Runnable>(0).run()
                true
            }
            `when`(handler.postDelayed(any(Runnable::class.java), anyLong())).thenAnswer {
                timeout = it.getArgument(0)
                true
            }
            compat.`when`<Intent> {
                ContextCompat.registerReceiver(any(Context::class.java),
                    any(BroadcastReceiver::class.java), any(IntentFilter::class.java), anyInt())
            }.thenAnswer {
                receivers.add(it.getArgument(1))
                null
            }
            pendingIntents.`when`<PendingIntent> {
                PendingIntent.getBroadcast(any(Context::class.java), anyInt(),
                    nullable(Intent::class.java), anyInt())
            }.thenReturn(mock(PendingIntent::class.java))
            manager = UsbReadinessManager(context, handler, Executor { it.run() }, printer)
        }

        fun ensure(): Result = mock(Result::class.java).also { manager.ensure("0483:5743", it) }

        fun event(action: String, target: UsbDevice = device) {
            val intent = mock(Intent::class.java)
            `when`(intent.action).thenReturn(action)
            @Suppress("DEPRECATION")
            `when`(intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)).thenReturn(target)
            receivers.first().onReceive(context, intent)
        }

        fun permissionResponse(granted: Boolean, receiver: BroadcastReceiver = receivers.last()) {
            permission = granted
            val intent = mock(Intent::class.java)
            `when`(intent.action).thenReturn(action)
            @Suppress("DEPRECATION")
            `when`(intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)).thenReturn(device)
            `when`(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)).thenReturn(granted)
            receiver.onReceive(context, intent)
        }

        override fun close() {
            manager.close()
            intents.close()
            pendingIntents.close()
            compat.close()
        }
    }

    private fun status(result: Result): String {
        val response = mockingDetails(result).invocations.single { it.method.name == "success" }
        return (response.arguments[0] as Map<*, *>)["status"] as String
    }

    @Test fun alreadyPermittedDeviceOpensWithoutDialog() {
        Fixture(device("/usb/1", 1)).use { f ->
            assertEquals("ready", status(f.ensure()))
            verify(f.usb, never()).requestPermission(any(UsbDevice::class.java), any(PendingIntent::class.java))
            verify(f.printer).connectUsbPrinter("/usb/1")
        }
    }

    @Test fun reconnectResolvesNewAddressAndCleansOldSession() {
        Fixture(device("/usb/1", 1)).use { f ->
            assertEquals("ready", status(f.ensure()))
            clearInvocations(f.printer)
            f.devices.clear()
            f.event(UsbManager.ACTION_USB_DEVICE_DETACHED)
            verify(f.printer).deInitPrinter()
            assertEquals("notFound", status(f.ensure()))
            val replacement = device("/usb/2", 2)
            f.devices[replacement.deviceName] = replacement
            f.event(UsbManager.ACTION_USB_DEVICE_ATTACHED, replacement)
            assertEquals("ready", status(f.ensure()))
            verify(f.printer).connectUsbPrinter("/usb/2")
        }
    }

    @Test fun permissionMustCompleteBeforeConnectingAndConcurrentRequestIsBusy() {
        Fixture(device("/usb/1", 1)).use { f ->
            f.permission = false
            val result = f.ensure()
            verifyNoInteractions(result, f.printer)
            assertEquals("busy", status(f.ensure()))
            f.permissionResponse(true)
            assertEquals("ready", status(result))
            verify(f.printer).connectUsbPrinter("/usb/1")
        }
    }

    @Test fun deniedPermissionDoesNotConnectAndCanBeRetried() {
        Fixture(device("/usb/1", 1)).use { f ->
            f.permission = false
            val denied = f.ensure()
            f.permissionResponse(false)
            assertEquals("permissionDenied", status(denied))
            verifyNoInteractions(f.printer)
            val retry = f.ensure()
            f.permissionResponse(true)
            assertEquals("ready", status(retry))
        }
    }

    @Test fun detachDuringPermissionCompletesOnceAndIgnoresLateGrant() {
        Fixture(device("/usb/1", 1)).use { f ->
            f.permission = false
            val result = f.ensure()
            val permissionReceiver = f.receivers.last()
            f.devices.clear()
            f.event(UsbManager.ACTION_USB_DEVICE_DETACHED)
            f.permissionResponse(true, permissionReceiver)
            assertEquals("disconnected", status(result))
            verifyNoInteractions(f.printer)
            verify(f.context).unregisterReceiver(permissionReceiver)
        }
    }

    @Test fun permissionTimeoutCleansReceiverAndAllowsRetry() {
        Fixture(device("/usb/1", 1)).use { f ->
            f.permission = false
            val result = f.ensure()
            val receiver = f.receivers.last()
            assertNotNull(f.timeout).run()
            assertEquals("permissionTimeout", status(result))
            verify(f.context).unregisterReceiver(receiver)
            f.permissionResponse(true, receiver)
            verifyNoInteractions(f.printer)
            assertEquals("ready", status(f.ensure()))
        }
    }

    @Test fun detachDuringConnectCannotReportReady() {
        Fixture(device("/usb/1", 1)).use { f ->
            `when`(f.printer.connectUsbPrinter("/usb/1")).thenAnswer {
                f.devices.clear() // Device enumeration is authoritative even without a broadcast.
                0
            }
            assertEquals("disconnected", status(f.ensure()))
        }
    }

    @Test fun failedSdkConnectionIsNotReady() {
        Fixture(device("/usb/1", 1)).use { f ->
            `when`(f.printer.connectUsbPrinter("/usb/1")).thenReturn(-2)
            assertEquals("connectionFailed", status(f.ensure()))
        }
    }

    @Test fun usbDetachDoesNotCloseAnotherTransportAfterSwitch() {
        Fixture(device("/usb/1", 1)).use { f ->
            f.ensure()
            f.manager.releaseConnection() // Plugin calls this before opening LAN/serial.
            clearInvocations(f.printer)
            f.devices.clear()
            f.event(UsbManager.ACTION_USB_DEVICE_DETACHED)
            verifyNoInteractions(f.printer)
        }
    }

    @Test fun engineDetachCompletesPendingRequestAndUnregistersReceivers() {
        val f = Fixture(device("/usb/1", 1))
        f.permission = false
        val result = f.ensure()
        val permissionReceiver = f.receivers.last()
        f.close()
        assertEquals("unavailable", status(result))
        verify(f.context).unregisterReceiver(permissionReceiver)
        verify(f.context).unregisterReceiver(f.receivers.first())
    }
}
