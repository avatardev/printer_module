import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:printer_module/printer_module.dart';

import 'data_selection_sheet.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  PrinterType? _printerType;
  String? _usbIdentity;
  bool _printing = false;
  String _platformVersion = 'Unknown';
  final _printerModulePlugin = PrinterModule();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await _printerModulePlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  void connectPrinter() async {
    final result = await DataSelectionSheet.show(
      context: context,
      items: PrinterType.values,
      title: 'Pilih Printer',
      searchHint: 'Cari printer...',
      showSearch: true,
    );

    if (result != null) {
      if (result == PrinterType.universal) {
        final devices = await _printerModulePlugin.getUsbDevices();
        if (!mounted) return;
        final identities = devices
            .map((device) => device['identity'] as String)
            .toList();
        if (identities.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Printer USB belum terhubung.')),
          );
          return;
        }
        final identity = await showDialog<String>(
          context: context,
          builder: (dialogContext) => SimpleDialog(
            title: const Text('Pilih Printer USB'),
            children: identities
                .map(
                  (identity) => SimpleDialogOption(
                    onPressed: () => Navigator.pop(dialogContext, identity),
                    child: Text(identity),
                  ),
                )
                .toList(),
          ),
        );
        if (identity == null || !mounted) return;
        _usbIdentity = identity; // Persist this in the application's settings.
      } else {
        _usbIdentity = null;
        await _printerModulePlugin.connectPrinter(result);
      }
      _printerType = result;
    }
  }

  void statusPrinter() async {
    if (_printerType == null) return;
    final status = await _printerModulePlugin.printerStatus(_printerType!);
    if (!mounted) return;
    final snackBar = SnackBar(content: Text('Status Printer: $status'));
    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }

  void testPrinter() async {
    if (_printerType == null || _printing) return;
    setState(() => _printing = true);
    try {
      if (_printerType == PrinterType.universal) {
        final readiness = await _printerModulePlugin.ensureUsbReady(
          _usbIdentity!,
        );
        if (!mounted) return;
        if (!readiness.isReady) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text(readiness.message)));
          return;
        }
      }
      final commands = [
        PrintText('Hello, World!'),
        PrintText('This is a test.'),
      ];
      await _printerModulePlugin.prints(
        printerType: _printerType!,
        commands: commands,
      );
    } on PlatformException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message ?? 'Cetak gagal.')));
    } finally {
      if (mounted) setState(() => _printing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Plugin example app')),
      body: Column(
        children: [
          Center(child: Text('Running on: $_platformVersion\n')),
          SizedBox(height: 24.0),
          ElevatedButton(
            onPressed: _printing ? null : connectPrinter,
            child: Text("Connect Printer"),
          ),
          SizedBox(height: 24.0),
          ElevatedButton(
            onPressed: statusPrinter,
            child: Text("Status Printer"),
          ),
          SizedBox(height: 24.0),
          ElevatedButton(
            onPressed: _printing ? null : testPrinter,
            child: Text("Test Printer"),
          ),
        ],
      ),
    );
  }
}
