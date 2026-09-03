package com.avatarsolution.printer_module

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.icod.serial.SerialPortFinder
import com.szsicod.print.io.SerialAPI
import com.szsicod.print.io.SocketAPI
import com.szsicod.print.io.USBAPI
import com.szsicod.print.utils.BitmapUtils
import java.io.File
import java.util.Hashtable
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap
import com.szsicod.print.escpos.PrinterAPI

class PrinterHelperUniversalImpl(private val context: Context) : PrinterHelper {
    companion object {
        fun formatLeftRight(msg1: String, msg2: String, printSize: Int = 58): String {
            val printWidth = if (printSize == 80) 48 else 32
            val leftPadding = printWidth - msg1.length - msg2.length
            if (leftPadding >= 0) {
                return msg1 + " ".repeat(leftPadding) + msg2
            } else {
                val leftTrimmed = msg1.take(printWidth - msg2.length)
                return leftTrimmed + msg2
            }
        }

        fun formatThreeLine(
            leftText: String,
            centerText: String,
            rightText: String,
            printSize: Int = 58
        ): String {
            val printWidth = if (printSize == 80) 48 else 32
            var formattedText = leftText
            val spaceAvailable = printWidth -
                    (centerText.length + rightText.length) -
                    formattedText.length
            val midSpaceAvailable = spaceAvailable / 2
            for (i in 0 until spaceAvailable) {
                formattedText += if (i == midSpaceAvailable) {
                    " $centerText"
                } else {
                    " "
                }
            }
            formattedText += rightText
            return formattedText
        }

        fun strLine(printSize: Int = 58): String {
            val printWidth = if (printSize == 80) 48 else 32
            return String(CharArray(printWidth)).replace("\u0000", "-")
        }

        fun generateQRCode(text: String, size: Int): Bitmap? {
            try {
                val qrCodeWriter = QRCodeWriter()

                // Menyiapkan parameter untuk kode QR
                val hints = Hashtable<EncodeHintType, Any>()
                hints[EncodeHintType.MARGIN] = 1 // Margin (spacing) di sekitar QR Code
                hints[EncodeHintType.ERROR_CORRECTION] =
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L // Pengaturan koreksi kesalahan (misalnya, tingkat koreksi rendah)

                // Encode teks menjadi matrix QR Code
                val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size, hints)

                // Konversi matrix menjadi bitmap
                val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap[x, y] = if (bitMatrix.get(
                                x,
                                y
                            )
                        ) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    }
                }

                return bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }

    private var mPrinter: PrinterAPI = PrinterAPI.getInstance()

    override fun getUsbDevices(): MutableList<Map<String, Any>> {
        val usbManager = ContextCompat.getSystemService(context, UsbManager::class.java)

        if (usbManager == null) {
            return arrayListOf()
        } else {
            val deviceList = usbManager.deviceList

            Log.d("PrinterHelperImpl", deviceList.toString())

            // If no devices found
            if (deviceList.isEmpty()) {
                return arrayListOf()
            }

            // Create a list to hold device info
            val devicesInfo = mutableListOf<Map<String, Any>>()

            // Iterate over devices and extract relevant information
            for ((_, device) in deviceList) {
                val deviceInfo = mutableMapOf<String, Any>()
                deviceInfo["deviceId"] = device.deviceId
                deviceInfo["vendorId"] = device.vendorId
                deviceInfo["productId"] = device.productId
                deviceInfo["deviceName"] = device.deviceName
                deviceInfo["identity"] = UsbDeviceResolver.identityOf(device)
                deviceInfo["manufacturerName"] = device.manufacturerName ?: ""
                deviceInfo["productName"] = device.productName ?: ""

                // Add device info to the list
                devicesInfo.add(deviceInfo)
            }

            // Send the list of device info to Flutter
            return devicesInfo
        }
    }

    override fun getSerialDevices(): MutableList<String> {
        var mSerialPortList = SerialPortFinder.getAllDevicesPath().toMutableList()
        mSerialPortList.forEach {
            it.toString()
        }
        if (mSerialPortList.isEmpty()) mSerialPortList = arrayListOf()
        return mSerialPortList
    }

    override fun connectPrinter(): Int {
        return  -1
    }

    override fun connectUsbPrinter(deviceId: String): Int {
        var result = -1
        val thread = Thread {
            try {
                val usbManager = ContextCompat.getSystemService(context, UsbManager::class.java)

                if(usbManager == null) {
                    result = -99
                    return@Thread
                }

                if(mPrinter.isConnect) {
                    mPrinter.disconnect()
                }

                val device = UsbDeviceResolver.findDevice(usbManager, deviceId)

                if (device != null) {
                    val io = USBAPI(ReceiverCompatContext(context), device)
                    result = mPrinter.connect(io)
                    Log.d("PrinterHelperImpl", result.toString())
                } else {
                    result = -1
                }
            } catch (e: Exception) {
                Log.e("PrinterHelperImpl", "USB connection failed", e)
                result = -1
            }
        }
        thread.start()
        thread.join()

        return result
    }

    override fun connectSerialPrinter(deviceAddress: String, baudRate: Int, flowControl: Int): Int {
        var result = -1

        val thread = Thread {
            try {
                if(mPrinter.isConnect) {
                    mPrinter.disconnect()
                }

                val io = SerialAPI(File(deviceAddress), baudRate, flowControl)
                result = mPrinter.connect(io)
                Log.d("PrinterHelperImpl", result.toString())
            } catch (e: Exception) {
                Log.e("PrinterHelperImpl", "Serial connection failed", e)
                result = -1
            }
        }
        thread.start()
        thread.join()

        return result
    }

    override fun connectSocketPrinter(deviceAddress: String, devicePort: Int): Int {
        var result = -1

        val thread = Thread {
            try {
                if(mPrinter.isConnect) {
                    mPrinter.disconnect()
                }

                val io = SocketAPI(deviceAddress, devicePort)
                result = mPrinter.connect(io)
                Log.d("PrinterHelperImpl", result.toString())

            } catch (e: Exception) {
                Log.e("PrinterHelperImpl", "LAN connection failed", e)
                result = -1
            }
        }
        thread.start()
        thread.join()

        return result
    }

    override fun initPrinter() {
        return
    }

    override fun deInitPrinter() {
        // Called on the plugin's printer executor. Propagate failures to its
        // USB lifecycle manager instead of losing them on a nested thread.
        mPrinter.disconnect()
    }

    override fun reset() {
        val thread = Thread {
            // resetPrinter() sends the vendor-proprietary `ESC FD FD flashE`
            // flash-reset that generic LAN printers do not implement and print
            // literally as "²flashE"; standard ESC @ initializes instead.
            mPrinter.init()
        }
        thread.start()
        thread.join()
    }

    override fun print(data: String, fontSize: Int, align: Int, isBold: Boolean, printSize: Int?) {
        val thread = Thread {
            mPrinter.setAlignMode(align)
            mPrinter.fontSizeSet(fontSize)
            mPrinter.setFontStyle(0, false, false, false, isBold)
            mPrinter.printString(data, "GBK", true)
        }
        thread.start()
        thread.join()
    }

    override fun startPrint() {
    }

    override fun printStrLine(printSize: Int?) {
        val thread = Thread {
            mPrinter.printString(strLine(printSize ?: 58), "GBK", true)
        }
        thread.start()
        thread.join()
    }

    override fun printLeftRight(data1: String, data2: String, printSize: Int?) {
        val thread = Thread {
            mPrinter.fontSizeSet(18)
            mPrinter.setFontStyle(0, false, false, false, false)
            if (printSize != null) {
                mPrinter.printString(
                    formatLeftRight(data1, data2, printSize),
                    "GBK",
                    true
                )
            } else {
                mPrinter.printString(
                    formatLeftRight(data1, data2),
                    "GBK",
                    true
                )
            }
        }
        thread.start()
        thread.join()
    }

    override fun printThreeLineText(data1: String, data2: String, data3: String, printSize: Int?) {
        val thread = Thread {
            mPrinter.fontSizeSet(18)
            mPrinter.setFontStyle(0, false, false, false, false)
            if (printSize != null) {
                mPrinter.printString(
                    formatThreeLine(data1, data2, data3, printSize),
                    "GBK",
                    true
                )
            } else {
                mPrinter.printString(
                    formatThreeLine(data1, data2, data3),
                    "GBK",
                    true
                )
            }
        }
        thread.start()
        thread.join()
    }

    override fun printSingleBitmap(image: Bitmap, align: Int, printSize: Int?) {
        val thread = Thread {
            try {
                val maxPrintWidth = if (printSize == 80) 576 else 384
                // ESC/POS raster rows are byte-aligned (8 pixels). Sending a
                // 100 px bitmap leaves row padding that some printer SDKs draw
                // as a vertical line. Small logos are enlarged moderately;
                // every image is flattened on white and aligned to 8 pixels.
                val requestedWidth = if (image.width <= 120) {
                    (image.width * 1.44f).toInt()
                } else {
                    image.width
                }.coerceAtMost(maxPrintWidth)
                val targetWidth = (requestedWidth / 8 * 8).coerceAtLeast(8)
                val targetHeight =
                    (image.height.toFloat() * targetWidth / image.width).toInt().coerceAtLeast(1)
                val printableBitmap = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(printableBitmap)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(
                    image,
                    null,
                    Rect(0, 0, targetWidth, targetHeight),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )

                mPrinter.setAlignMode(align)
                mPrinter.printRasterBitmap(printableBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread.start()
        thread.join()
    }

    override fun printQr(data: String, align: Int, size: Int, printSize: Int?) {
        val thread = Thread {
            // iMin treats `size` as QR module size, while the universal
            // backend generates a bitmap and therefore needs pixels. Convert
            // legacy module-sized values into a readable thermal-print size.
            val maxQrSize = if (printSize == 80) 320 else 240
            val qrPixelSize = if (size <= 32) {
                (size * 16).coerceIn(160, maxQrSize)
            } else {
                size.coerceIn(160, maxQrSize)
            }
            val image = generateQRCode(data, qrPixelSize)
            if (image != null) {
                try {
                    mPrinter.setAlignMode(align)
                    mPrinter.printRasterBitmap(image)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        thread.start()
        thread.join()
    }

    override fun getStatus(): Int {
        var result = 0
        val thread = Thread {
            result = if (mPrinter.isConnect) 1 else 0
        }
        thread.start()
        thread.join()

        return result
    }

    override fun feedPaper(size: Int, printSize: Int?) {
        val thread = Thread {
            mPrinter.printFeed()
        }
        thread.start()
        thread.join()
    }

    override fun partialCut() {
        val thread = Thread {
            mPrinter.cutPaper(66, 0)
        }
        thread.start()
        thread.join()
    }

    override fun printBitmap(bitmap: ByteArray, with: Int, height: Int, printSize: Int?) { }

    override fun enterPrinterBuffer() {
        return
    }

    override fun commitPrinterBuffer(callback: (Int) -> Unit) {
        return
    }

    override fun exitPrinterBuffer() {
        return
    }
}
