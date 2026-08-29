import 'dart:io';
import 'dart:math';

class NetworkPrinter {
  final String ipAddress;
  final int port;

  const NetworkPrinter({required this.ipAddress, required this.port});

  Map<String, dynamic> toJson() => {'ipAddress': ipAddress, 'port': port};

  @override
  String toString() => 'NetworkPrinter($ipAddress:$port)';

  @override
  bool operator ==(Object other) =>
      other is NetworkPrinter &&
      other.ipAddress == ipAddress &&
      other.port == port;

  @override
  int get hashCode => Object.hash(ipAddress, port);
}

class PrinterModuleNetwork {
  static PrinterModuleNetwork _instance = PrinterModuleNetwork();

  /// The default instance of [PrinterModuleNetwork] to use.
  static PrinterModuleNetwork get instance => _instance;

  static set instance(PrinterModuleNetwork instance) {
    _instance = instance;
  }

  /// Port used by most network thermal printers (JetDirect/raw printing).
  static const int defaultPort = 9100;
  static const Duration defaultTimeout = Duration(milliseconds: 500);
  static const int defaultConcurrency = 64;

  /// Scans the device's /24 IPv4 subnet for hosts that accept a TCP
  /// connection on the printer port (9100 by default).
  ///
  /// [subnet] overrides the first three octets to scan, e.g. `192.168.1`.
  /// When omitted the subnet is taken from the device's Wi-Fi address.
  Future<List<NetworkPrinter>> discoverNetworkPrinters({
    int port = defaultPort,
    Duration timeout = defaultTimeout,
    String? subnet,
    int concurrency = defaultConcurrency,
  }) async {
    final targetSubnet = subnet ?? await _detectSubnet();
    if (targetSubnet == null) {
      print('Network printer discovery skipped: no IPv4 subnet detected.');
      return const [];
    }
    _validateSubnet(targetSubnet);

    final addresses = [for (int i = 1; i <= 254; i++) '$targetSubnet.$i'];
    final printers = <NetworkPrinter>[];

    for (int i = 0; i < addresses.length; i += concurrency) {
      final batchEnd = min(i + concurrency, addresses.length);
      final batch = await Future.wait(
        addresses.sublist(i, batchEnd).map(
          (address) => _probePort(address, port, timeout),
        ),
      );
      printers.addAll(batch.whereType<NetworkPrinter>());
    }

    printers.sort(
      (a, b) => int.parse(
        a.ipAddress.split('.').last,
      ).compareTo(int.parse(b.ipAddress.split('.').last)),
    );
    return printers;
  }

  Future<NetworkPrinter?> _probePort(
    String address,
    int port,
    Duration timeout,
  ) async {
    try {
      final socket = await Socket.connect(address, port, timeout: timeout);
      socket.destroy();
      return NetworkPrinter(ipAddress: address, port: port);
    } catch (_) {
      return null;
    }
  }

  Future<String?> _detectSubnet() async {
    final interfaces = await NetworkInterface.list(
      type: InternetAddressType.IPv4,
      includeLoopback: false,
      includeLinkLocal: false,
    );
    for (final interface in interfaces) {
      for (final address in interface.addresses) {
        final octets = address.address.split('.');
        if (octets.length == 4) {
          return octets.sublist(0, 3).join('.');
        }
      }
    }
    return null;
  }

  void _validateSubnet(String subnet) {
    final parts = subnet.split('.');
    final isValid =
        parts.length == 3 &&
        parts.every((part) {
          final value = int.tryParse(part);
          return value != null && value >= 0 && value <= 255;
        });
    if (!isValid) {
      throw ArgumentError.value(
        subnet,
        'subnet',
        'Must be the first three octets of an IPv4 address, e.g. "192.168.1".',
      );
    }
  }
}
