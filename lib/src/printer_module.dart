import 'package:blue_thermal_printer/blue_thermal_printer.dart';
import 'package:printer_module/src/printer_module_bluetooth.dart';

import 'print_command.dart';
import 'printer_module_network.dart';
import 'printer_module_platform_interface.dart';

enum PrinterType { imin, telpo, bluetooth, universal }

class PrinterModule {
  Future<String?> getPlatformVersion() {
    return PrinterModulePlatform.instance.getPlatformVersion();
  }

  Future<void> prints({
    required PrinterType printerType,
    required List<PrintCommand> commands,
  }) {
    if (printerType == PrinterType.bluetooth) {
      return PrinterModuleBluetooth.instance.handleBluetoothPrint(commands);
    }
    return PrinterModulePlatform.instance.prints(
      printerType: printerType,
      commands: commands,
    );
  }

  Future<int> printerStatus(PrinterType printerType) async {
    if (printerType == PrinterType.bluetooth) {
      final printerStatus = await PrinterModuleBluetooth.instance
          .isBluetoothConnected();
      return printerStatus == null
          ? -99
          : printerStatus
          ? 1
          : 0;
    }
    return PrinterModulePlatform.instance.printerStatus(printerType);
  }

  Future<List<BluetoothDevice>> getBluetoothDevices() {
    return PrinterModuleBluetooth.instance.getBluetoothDevices();
  }

  Future<bool> connectBluetooth(BluetoothDevice device) {
    return PrinterModuleBluetooth.instance.connectBluetooth(device);
  }

  Future<bool> disconnectBluetooth() {
    return PrinterModuleBluetooth.instance.disconnectBluetooth();
  }

  Future<int> connectPrinter(PrinterType printerType) {
    return PrinterModulePlatform.instance.connectPrinter(printerType);
  }

  Future<int> connectUsbPrinter(String deviceId) {
    return PrinterModulePlatform.instance.connectUsbPrinter(deviceId);
  }

  Future<bool> requestUsbPermission(String deviceId) {
    return PrinterModulePlatform.instance.requestUsbPermission(deviceId);
  }

  Future<int> connectSerialPrinter(
    String deviceAddress,
    int baudRate,
    int flowControl,
  ) {
    return PrinterModulePlatform.instance.connectSerialPrinter(
      deviceAddress,
      baudRate,
      flowControl,
    );
  }

  Future<int> connectSocketPrinter(String deviceAddress, int devicePort) {
    return PrinterModulePlatform.instance.connectSocketPrinter(
      deviceAddress,
      devicePort,
    );
  }

  /// Finds network printers on the local /24 subnet by probing the printer
  /// port (9100 by default). Results can be passed to [connectSocketPrinter].
  Future<List<NetworkPrinter>> discoverNetworkPrinters({
    int port = PrinterModuleNetwork.defaultPort,
    Duration timeout = PrinterModuleNetwork.defaultTimeout,
    String? subnet,
    int concurrency = PrinterModuleNetwork.defaultConcurrency,
  }) {
    return PrinterModuleNetwork.instance.discoverNetworkPrinters(
      port: port,
      timeout: timeout,
      subnet: subnet,
      concurrency: concurrency,
    );
  }

  Future<List<Map<String, dynamic>>> getUsbDevices() {
    return PrinterModulePlatform.instance.getUsbDevices();
  }

  Future<List<String>> getSerialDevices() {
    return PrinterModulePlatform.instance.getSerialDevices();
  }
}
