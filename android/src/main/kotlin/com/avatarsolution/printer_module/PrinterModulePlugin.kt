package com.avatarsolution.printer_module

import android.graphics.BitmapFactory
import android.app.PendingIntent
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** PrinterModulePlugin */
class PrinterModulePlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var context: Context? = null
  private var activity: Activity? = null

  /// MethodChannel handlers always run on the Android main thread, but printer
  /// I/O (socket, serial, USB) is forbidden there (NetworkOnMainThreadException),
  /// so every printer operation is serialized through this worker and its
  /// result is posted back to the main thread, as MethodChannel requires.
  private val printerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "printer-module-io").apply { isDaemon = true }
  }
  private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "printer_module")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "getPlatformVersion") {
      result.success("Android ${android.os.Build.VERSION.RELEASE}")
    } else if (call.method == "printReceipt") {
      val printerTypeStr = call.argument<String>("printerType")
      val commands = call.argument<List<Map<String, Any>>>("commands")
      if (printerTypeStr != null && commands != null) {
        runOnPrinterThread(result, "PRINT_ERROR", "Printer failed to print.") {
          processAndPrint(printerTypeStr, commands)
          null
        }
      } else {
        result.error("INVALID_ARGUMENTS", "Printer type or commands are null.", null)
      }
    } else if (call.method == "printerStatus") {
      val printerTypeStr = call.argument<String>("printerType")
      if (printerTypeStr != null) {
        runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
          val status = statusPrinter(printerTypeStr)
          if (status == -99) throw IllegalStateException("Plugin error.")
          status
        }
      } else {
        result.error("INVALID_ARGUMENTS", "Printer type is null.", null)
      }
    } else if (call.method == "connectPrinter") {
      val printerTypeStr = call.argument<String>("printerType")
      if (printerTypeStr != null) {
        runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
          val status = connectPrinter(printerTypeStr)
          if (status == -99) throw IllegalStateException("Plugin error.")
          status
        }
      } else {
        result.error("INVALID_ARGUMENTS", "Printer type is null.", null)
      }
    } else if(call.method == "connectUsbPrinter") {
      val deviceId = call.argument<String>("deviceId")
      if (deviceId != null) {
        runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
          val status = connectUsbPrinter(deviceId)
          if (status == -99) throw IllegalStateException("Plugin error.")
          status
        }
      } else {
        result.error("INVALID_ARGUMENTS", "Device ID is null.", null)
      }
    } else if(call.method == "requestUsbPermission") {
      val deviceId = call.argument<String>("deviceId")
      if (deviceId.isNullOrBlank()) {
        result.error("INVALID_USB", "USB device path is empty.", null)
      } else {
        requestUsbPermission(deviceId, result)
      }
    } else if(call.method == "connectSerialPrinter") {
      val deviceAddress = call.argument<String>("deviceAddress")
      val baudRate = call.argument<Int>("baudRate")
      val flowControl = call.argument<Int>("flowControl")
      if (deviceAddress != null && baudRate != null && flowControl != null) {
        runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
          val status = connectSerialPrinter(deviceAddress, baudRate, flowControl)
          if (status == -99) throw IllegalStateException("Plugin error.")
          status
        }
      } else {
        result.error("INVALID_ARGUMENTS", "Device address, baud rate, or flow control is null.", null)
      }
    } else if(call.method == "connectSocketPrinter") {
      val deviceAddress = call.argument<String>("deviceAddress")
      val devicePort = call.argument<Int>("devicePort")
      if (deviceAddress != null && devicePort != null) {
        runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
          val status = connectSocketPrinter(deviceAddress, devicePort)
          if (status == -99) throw IllegalStateException("Plugin error.")
          status
        }
      } else {
        result.error("INVALID_ARGUMENTS", "Device address or device port is null.", null)
      }
    } else if(call.method == "getUsbDevices") {
      runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
        getUsbDevices()
      }
    } else if(call.method == "getSerialDevices") {
      runOnPrinterThread(result, "PLUGIN_ERROR", "Plugin error.") {
        getSerialDevices()
      }
    } else {
      result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    context = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  private fun runOnPrinterThread(result: Result, errorCode: String, fallbackMessage: String, work: () -> Any?) {
    printerExecutor.execute {
      try {
        val value = work()
        mainHandler.post { result.success(value) }
      } catch (e: Exception) {
        mainHandler.post {
          result.error(errorCode, e.message ?: fallbackMessage, null)
        }
      }
    }
  }

  private fun getPrinterHelper(printerType: String): PrinterHelper {
    val currentContext = activity ?: context ?: throw IllegalStateException("Context not available")
    return when (printerType) {
      "imin" -> PrinterHelperIminImpl(currentContext)
      "telpo" -> PrinterHelperTelpoImpl(currentContext)
      "universal" -> PrinterHelperUniversalImpl(currentContext)
      else -> throw IllegalArgumentException("Unknown printer type: $printerType")
    }
  }

  private fun statusPrinter(printerType: String): Int {
    val printerHelper: PrinterHelper = getPrinterHelper(printerType)
    return printerHelper.getStatus()
  }

  private fun connectPrinter(printerType: String): Int {
    val printerHelper: PrinterHelper = getPrinterHelper(printerType)
    return printerHelper.connectPrinter()
  }

  private fun connectUsbPrinter(deviceId: String): Int {
    val printerHelper: PrinterHelper = getPrinterHelper("universal")
    return printerHelper.connectUsbPrinter(deviceId)
  }

  private fun requestUsbPermission(deviceId: String, result: Result) {
    val currentContext = activity ?: context
    if (currentContext == null) {
      result.error("NO_CONTEXT", "Android context is not available.", null)
      return
    }
    val usbManager = currentContext.getSystemService(Context.USB_SERVICE) as UsbManager
    val device = usbManager.deviceList[deviceId]
    if (device == null) {
      result.error("USB_NOT_FOUND", "USB printer is no longer connected.", null)
      return
    }
    if (usbManager.hasPermission(device)) {
      result.success(true)
      return
    }

    val action = "${currentContext.packageName}.PRINTER_MODULE_USB_PERMISSION"
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context, intent: Intent) {
        if (intent.action != action) return
        currentContext.unregisterReceiver(this)
        result.success(
          intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false),
        )
      }
    }
    ContextCompat.registerReceiver(
      currentContext,
      receiver,
      IntentFilter(action),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    val permissionIntent = PendingIntent.getBroadcast(
      currentContext,
      0,
      Intent(action).setPackage(currentContext.packageName),
      flags,
    )
    usbManager.requestPermission(device, permissionIntent)
  }

  private fun connectSerialPrinter(deviceAddress: String, baudRate: Int, flowControl: Int): Int {
    val printerHelper: PrinterHelper = getPrinterHelper("universal")
    return printerHelper.connectSerialPrinter(deviceAddress, baudRate, flowControl)
  }

  private fun connectSocketPrinter(deviceAddress: String, devicePort: Int): Int {
    val printerHelper: PrinterHelper = getPrinterHelper("universal")
    return printerHelper.connectSocketPrinter(deviceAddress, devicePort)
  }

  private fun getUsbDevices(): List<Map<String, Any>> {
    val printerHelper: PrinterHelper = getPrinterHelper("universal")
    return printerHelper.getUsbDevices()
  }

  private fun getSerialDevices(): List<String> {
    val printerHelper: PrinterHelper = getPrinterHelper("universal")
    return printerHelper.getSerialDevices()
  }

  private fun processAndPrint(printerType: String, commands: List<Map<String, Any>>) {
    val printerHelper: PrinterHelper = getPrinterHelper(printerType)
    if (printerType == "universal" && printerHelper.getStatus() != 1) {
      throw IllegalStateException("Universal printer is not connected.")
    }
    for (command in commands) {
      when (command["type"] as? String) {
        "text" -> {
          val text = command["text"] as String
          val fontSize = command["fontSize"] as Int
          val align = command["align"] as Int
          val isBold = command["isBold"] as Boolean
          val printSize = command["printSize"] as Int?
          printerHelper.print(text, fontSize = fontSize, align = align, isBold = isBold, printSize = printSize)
        }
        "separator" -> {
          val printSize = command["printSize"] as Int?
          printerHelper.printStrLine(printSize = printSize)
        }
        "feed" -> {
          val lines = command["lines"] as Int
          val printSize = command["printSize"] as Int?
          printerHelper.feedPaper(lines, printSize = printSize)
        }
        "cut" -> {
          printerHelper.partialCut()
        }
        "leftRight" -> {
          val leftText = command["leftText"] as String
          val rightText = command["rightText"] as String
          val printSize = command["printSize"] as Int?
          printerHelper.printLeftRight(leftText, rightText, printSize = printSize)
        }
        "threeLines" -> {
          val leftText = command["leftText"] as String
          val centerText = command["centerText"] as String
          val rightText = command["rightText"] as String
          val printSize = command["printSize"] as Int?
          printerHelper.printThreeLineText(leftText, centerText, rightText, printSize = printSize)
        }
        "qr" -> {
          val qrText = command["qrText"] as String
          val align = command["align"] as Int
          val size = command["size"] as Int
          val printSize = command["printSize"] as Int?
          printerHelper.printQr(qrText, align, size, printSize = printSize)
        }
        "bitmap" -> {
          val imageStr = command["imageStr"] as String
          val imageWidth = command["imageWidth"] as Int
          val imageHeight = command["imageHeight"] as Int
          val printSize = command["printSize"] as Int?
          val imageBytes = Base64.decode(imageStr, 0)
          printerHelper.printBitmap(imageBytes, imageWidth, imageHeight, printSize = printSize)
        }
        "singleBitmap" -> {
          val imageStr = command["imageStr"] as String
          val align = command["align"] as Int
          val printSize = command["printSize"] as Int?
          val imageBytes = Base64.decode(imageStr, 0)
          val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
          printerHelper.printSingleBitmap(image, align, printSize = printSize)
        }
        "reset" -> {
          printerHelper.reset()
        }
        "startPrint" -> {
          printerHelper.startPrint()
        }
      }
    }
  }
}
