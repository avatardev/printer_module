import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:printer_module/printer_module.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('NetworkPrinter', () {
    test('toJson exposes ip address and port', () {
      expect(
        const NetworkPrinter(ipAddress: '192.168.1.100', port: 9100).toJson(),
        {'ipAddress': '192.168.1.100', 'port': 9100},
      );
    });

    test('equality is based on ip address and port', () {
      const printer = NetworkPrinter(ipAddress: '192.168.1.100', port: 9100);
      expect(printer, const NetworkPrinter(ipAddress: '192.168.1.100', port: 9100));
      expect(
        printer == const NetworkPrinter(ipAddress: '192.168.1.101', port: 9100),
        isFalse,
      );
    });
  });

  test('discoverNetworkPrinters finds a listening printer port', () async {
    final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);

    final printers = await PrinterModule().discoverNetworkPrinters(
      port: server.port,
      subnet: '127.0.0',
      timeout: const Duration(milliseconds: 250),
      concurrency: 64,
    );

    expect(
      printers,
      contains(NetworkPrinter(ipAddress: '127.0.0.1', port: server.port)),
    );
  });

  test('discoverNetworkPrinters returns empty when no printer is found',
      () async {
    final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final port = server.port;
    await server.close();

    final printers = await PrinterModule().discoverNetworkPrinters(
      port: port,
      subnet: '127.0.0',
      timeout: const Duration(milliseconds: 250),
      concurrency: 64,
    );

    expect(printers, isEmpty);
  });

  test('discoverNetworkPrinters rejects an invalid subnet', () {
    expect(
      () => PrinterModule().discoverNetworkPrinters(subnet: '192.168.1.5'),
      throwsArgumentError,
    );
  });
}
