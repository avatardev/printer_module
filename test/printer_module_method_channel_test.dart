import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:printer_module/src/printer_module_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelPrinterModule platform = MethodChannelPrinterModule();
  const MethodChannel channel = MethodChannel('printer_module');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
          return '42';
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });

  test('getUsbDevices converts method channel values to typed maps', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
          expect(methodCall.method, 'getUsbDevices');
          return <Object?>[
            <Object?, Object?>{
              'deviceId': 1,
              'deviceName': 'USB Printer',
            },
          ];
        });

    expect(await platform.getUsbDevices(), <Map<String, dynamic>>[
      <String, dynamic>{
        'deviceId': 1,
        'deviceName': 'USB Printer',
      },
    ]);
  });
}
