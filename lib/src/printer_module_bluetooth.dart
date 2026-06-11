import 'package:blue_thermal_printer/blue_thermal_printer.dart';

import 'print_command.dart';
import 'printer_utils.dart';

class PrinterModuleBluetooth {
  final BlueThermalPrinter _bluetoothPrinter = BlueThermalPrinter.instance;

  static PrinterModuleBluetooth _instance = PrinterModuleBluetooth();

  /// The default instance of [PrinterModuleBluetooth] to use.
  static PrinterModuleBluetooth get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [PrinterModulePlatform] when
  /// they register themselves.
  static set instance(PrinterModuleBluetooth instance) {
    _instance = instance;
  }

  int _formatPrinterSize(int printSize) => printSize == 80 ? 1 : 0;

  Future<List<BluetoothDevice>> getBluetoothDevices() async {
    return await _bluetoothPrinter.getBondedDevices();
  }

  Future<bool> connectBluetooth(BluetoothDevice device) async {
    try {
      return await _bluetoothPrinter.connect(device) ?? false;
    } catch (e) {
      print("Error connecting to bluetooth device: $e");
      return false;
    }
  }

  Future<bool> disconnectBluetooth() async {
    try {
      return await _bluetoothPrinter.disconnect() ?? false;
    } catch (e) {
      print("Error disconnecting from bluetooth device: $e");
      return false;
    }
  }

  Future<bool?> isBluetoothConnected() {
    return _bluetoothPrinter.isConnected;
  }

  Future<void> handleBluetoothPrint(List<PrintCommand> commands) async {
    bool? isConnected = await _bluetoothPrinter.isConnected;
    if (isConnected != true) {
      print("Error: Bluetooth printer is not connected.");
      return;
    }

    for (final command in commands) {
      if (command is PrintText) {
        int size = 0;
        if (command.fontSize == 1) size = 1;
        if (command.fontSize == 2) size = 2;

        await _bluetoothPrinter.printCustom(
          command.text,
          size,
          command.align.index,
        );
      } else if (command is PrintSeparator) {
        final formatPrinterSize = _formatPrinterSize(command.printSize);
        await _bluetoothPrinter.printNewLine();
        await _bluetoothPrinter.printCustom(
          PrinterUtils.generateStrLine(formatPrinterSize),
          0,
          1,
        );
        await _bluetoothPrinter.printNewLine();
      } else if (command is PrintFeed) {
        for (int i = 0; i < command.lines; i++) {
          await _bluetoothPrinter.printNewLine();
        }
      } else if (command is PrintCut) {
        await _bluetoothPrinter.paperCut();
      } else if (command is PrintSingleBitmap) {
        await _bluetoothPrinter.printImageBytes(command.imageBytes);
      } else if (command is PrintLeftRight) {
        final formatPrinterSize = _formatPrinterSize(command.printSize);
        await _bluetoothPrinter.printLeftRight(
          command.leftText,
          command.rightText,
          0,
          format: PrinterUtils.getLeftRightSpacingSize(formatPrinterSize),
        );
      } else if (command is PrintThreeLine) {
        final formatPrinterSize = _formatPrinterSize(command.printSize);
        await _bluetoothPrinter.print3Column(
          command.leftText,
          command.centerText,
          command.rightText,
          0,
          format: PrinterUtils.getThreeLineSpacingSize(formatPrinterSize),
        );
      }
    }
  }
}
