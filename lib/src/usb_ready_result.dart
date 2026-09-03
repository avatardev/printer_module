/// Outcome of preparing an Android USB printer for a new print job.
enum UsbReadyStatus {
  ready,
  notFound,
  permissionDenied,
  permissionTimeout,
  disconnected,
  busy,
  invalidIdentity,
  connectionFailed,
  unavailable,
}

class UsbReadyResult {
  const UsbReadyResult({
    required this.status,
    required this.message,
    this.connectionCode,
  });

  final UsbReadyStatus status;
  final String message;

  /// Raw printer SDK connection result, when a connection was attempted.
  final int? connectionCode;

  bool get isReady => status == UsbReadyStatus.ready;

  factory UsbReadyResult.fromMap(Map<Object?, Object?> map) {
    final status = UsbReadyStatus.values.firstWhere(
      (value) => value.name == map['status'],
      orElse: () => UsbReadyStatus.unavailable,
    );
    return UsbReadyResult(
      status: status,
      message: map['message'] as String? ?? 'Status USB tidak tersedia.',
      connectionCode: map['connectionCode'] as int?,
    );
  }
}
