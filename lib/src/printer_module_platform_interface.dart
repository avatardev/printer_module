import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'print_command.dart';
import 'printer_module.dart';
import 'printer_module_method_channel.dart';

abstract class PrinterModulePlatform extends PlatformInterface {
  /// Constructs a PrinterModulePlatform.
  PrinterModulePlatform() : super(token: _token);

  static final Object _token = Object();

  static PrinterModulePlatform _instance = MethodChannelPrinterModule();

  /// The default instance of [PrinterModulePlatform] to use.
  ///
  /// Defaults to [MethodChannelPrinterModule].
  static PrinterModulePlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [PrinterModulePlatform] when
  /// they register themselves.
  static set instance(PrinterModulePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<void> prints({
    required PrinterType printerType,
    required List<PrintCommand> commands,
  }) {
    throw UnimplementedError('printReceipt() has not been implemented.');
  }

  Future<int> printerStatus(PrinterType printerType) async {
    throw UnimplementedError('printerStatus() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>> getUsbDevices() {
    throw UnimplementedError('getUsbDevices() has not been implemented.');
  }

  Future<List<String>> getSerialDevices() {
    throw UnimplementedError('getSerialDevices() has not been implemented.');
  }

  Future<int> connectPrinter(PrinterType printerType) {
    throw UnimplementedError('connectPrinter() has not been implemented.');
  }

  Future<int> connectUsbPrinter(String deviceId) {
    throw UnimplementedError('connectUsbPrinter() has not been implemented.');
  }

  Future<bool> requestUsbPermission(String deviceId) {
    throw UnimplementedError(
      'requestUsbPermission() has not been implemented.',
    );
  }

  Future<int> connectSerialPrinter(
    String deviceAddress,
    int baudRate,
    int flowControl,
  ) {
    throw UnimplementedError(
      'connectSerialPrinter() has not been implemented.',
    );
  }

  Future<int> connectSocketPrinter(String deviceAddress, int devicePort) {
    throw UnimplementedError(
      'connectSocketPrinter() has not been implemented.',
    );
  }
}
