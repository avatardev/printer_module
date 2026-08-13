import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:printer_module/printer_module.dart';
import 'package:printer_module/src/printer_module_method_channel.dart';
import 'package:printer_module/src/printer_module_platform_interface.dart';

class MockPrinterModulePlatform
    with MockPlatformInterfaceMixin
    implements PrinterModulePlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<void> prints({
    required PrinterType printerType,
    required List<PrintCommand> commands,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<int> printerStatus(PrinterType printerType) {
    throw UnimplementedError();
  }

  @override
  Future<int> connectPrinter(PrinterType printerType) {
    throw UnimplementedError();
  }

  @override
  Future<int> connectSerialPrinter(
    String deviceAddress,
    int baudRate,
    int flowControl,
  ) {
    throw UnimplementedError();
  }

  @override
  Future<int> connectSocketPrinter(String deviceAddress, int devicePort) {
    throw UnimplementedError();
  }

  @override
  Future<int> connectUsbPrinter(String deviceId) {
    throw UnimplementedError();
  }

  @override
  Future<List<String>> getSerialDevices() {
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getUsbDevices() {
    throw UnimplementedError();
  }

  @override
  Future<bool> requestUsbPermission(String deviceId) {
    throw UnimplementedError();
  }
}

void main() {
  final PrinterModulePlatform initialPlatform = PrinterModulePlatform.instance;

  test('$MethodChannelPrinterModule is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelPrinterModule>());
  });

  test('getPlatformVersion', () async {
    PrinterModule printerModulePlugin = PrinterModule();
    MockPrinterModulePlatform fakePlatform = MockPrinterModulePlatform();
    PrinterModulePlatform.instance = fakePlatform;

    expect(await printerModulePlugin.getPlatformVersion(), '42');
  });
}
