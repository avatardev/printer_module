import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'print_command.dart';
import 'printer_module.dart';
import 'printer_module_platform_interface.dart';

/// An implementation of [PrinterModulePlatform] that uses method channels.
class MethodChannelPrinterModule extends PrinterModulePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('printer_module');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<void> prints({
    required PrinterType printerType,
    required List<PrintCommand> commands,
  }) async {
    final List<Map<String, dynamic>> commandsAsJson = commands
        .map((cmd) => cmd.toJson())
        .toList();

    await methodChannel.invokeMethod('printReceipt', {
      'printerType': printerType.name,
      'commands': commandsAsJson,
    });
  }

  @override
  Future<int> printerStatus(PrinterType printerType) async {
    try {
      final int status = await methodChannel.invokeMethod('printerStatus', {
        'printerType': printerType.name,
      });
      return status;
    } on PlatformException catch (e) {
      print("Failed to get printer status: '${e.message}'.");
      return -99;
    }
  }

  @override
  Future<int> connectPrinter(PrinterType printerType) async {
    try {
      final int status = await methodChannel.invokeMethod('connectPrinter', {
        'printerType': printerType.name,
      });
      return status;
    } on PlatformException catch (e) {
      print("Failed to connect printer: '${e.message}'.");
      return -99;
    }
  }

  @override
  Future<int> connectUsbPrinter(String deviceId) async {
    try {
      final int status = await methodChannel.invokeMethod('connectUsbPrinter', {
        'deviceId': deviceId,
      });
      return status;
    } on PlatformException catch (e) {
      print("Failed to connect usb printer: '${e.message}'.");
      return -99;
    }
  }

  @override
  Future<bool> requestUsbPermission(String deviceId) async {
    return await methodChannel.invokeMethod<bool>('requestUsbPermission', {
          'deviceId': deviceId,
        }) ??
        false;
  }

  @override
  Future<int> connectSerialPrinter(
    String deviceAddress,
    int baudRate,
    int flowControl,
  ) async {
    try {
      final int status = await methodChannel
          .invokeMethod('connectSerialPrinter', {
            'deviceAddress': deviceAddress,
            'baudRate': baudRate,
            'flowControl': flowControl,
          });
      return status;
    } on PlatformException catch (e) {
      print("Failed to connect serial printer: '${e.message}'.");
      return -99;
    }
  }

  @override
  Future<int> connectSocketPrinter(String deviceAddress, int devicePort) async {
    try {
      final int status = await methodChannel.invokeMethod(
        'connectSocketPrinter',
        {'deviceAddress': deviceAddress, 'devicePort': devicePort},
      );
      return status;
    } on PlatformException catch (e) {
      print("Failed to connect socket printer: '${e.message}'.");
      return -99;
    }
  }

  @override
  Future<List<Map<String, dynamic>>> getUsbDevices() async {
    try {
      final List<Map<String, dynamic>> devices = await methodChannel
          .invokeMethod('getUsbDevices');
      return devices;
    } on PlatformException catch (e) {
      print("Failed to get usb devices: '${e.message}'.");
      return [];
    }
  }

  @override
  Future<List<String>> getSerialDevices() async {
    try {
      final List<String> devices = await methodChannel.invokeMethod(
        'getSerialDevices',
      );
      return devices;
    } on PlatformException catch (e) {
      print("Failed to get serial devices: '${e.message}'.");
      return [];
    }
  }
}
