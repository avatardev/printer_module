import 'dart:async';

import 'package:flutter/services.dart';
import 'package:printer_module/printer_module.dart';
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
            <Object?, Object?>{'deviceId': 1, 'deviceName': 'USB Printer'},
          ];
        });

    expect(await platform.getUsbDevices(), <Map<String, dynamic>>[
      <String, dynamic>{'deviceId': 1, 'deviceName': 'USB Printer'},
    ]);
  });
  test(
    'USB readiness awaits native permission/connection completion',
    () async {
      final response = Completer<Map<String, Object?>>();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) {
            expect(call.method, 'ensureUsbReady');
            expect(call.arguments, {'deviceId': '0483:5743'});
            return response.future;
          });
      var completed = false;
      final pending = platform.ensureUsbReady('0483:5743').then((value) {
        completed = true;
        return value;
      });
      await Future<void>.delayed(Duration.zero);
      expect(completed, isFalse);
      response.complete({
        'status': 'ready',
        'message': 'Ready',
        'connectionCode': 0,
      });
      final ready = await pending;
      expect(ready.isReady, isTrue);
      expect(ready.connectionCode, 0);
    },
  );

  for (final status in UsbReadyStatus.values.where(
    (s) => s != UsbReadyStatus.ready,
  )) {
    test('USB ${status.name} never permits printing', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            return {'status': status.name, 'message': 'Try again'};
          });
      final result = await platform.ensureUsbReady('0483:5743');
      expect(result.status, status);
      expect(result.isReady, isFalse);
      expect(result.message, 'Try again');
    });
  }

  test('unknown or absent native USB status fails closed', () {
    expect(UsbReadyResult.fromMap({'status': 'futureStatus'}).isReady, isFalse);
    expect(UsbReadyResult.fromMap({}).status, UsbReadyStatus.unavailable);
  });
}
