# printer_module

Flutter printer module with Android USB, serial, LAN, and built-in printer support.

## Android USB: prepare before printing

Save the `identity` returned by `getUsbDevices()` (hex `vid:pid`, e.g.
`0483:5743`), not the changing `/dev/bus/usb/...` address. Call
`ensureUsbReady(identity)` once before each complete receipt:

```dart
final readiness = await printer.ensureUsbReady(savedUsbIdentity);
if (!readiness.isReady) {
  showMessage(readiness.message); // Application UI callback.
  return;
}

try {
  await printer.prints(
    printerType: PrinterType.universal,
    commands: commands,
  );
} on PlatformException catch (error) {
  showMessage(error.message ?? 'Cetak gagal.');
}
```

Import `package:flutter/services.dart` for `PlatformException` and
`package:printer_module/printer_module.dart` for the printer API.
Disable the print button while preparing/printing, and serialize whole jobs
(including preparation) in the application. Do not automatically retry a receipt
that may have printed partially.

The module resolves the current USB device, checks permission, awaits the Android
permission dialog if needed, clears the previous connection and opens a fresh
connection on its printer worker. Detach/attach events invalidate the old USB
session; they do not show dialogs or print in the background. The next preparation
call reconnects without restarting the app or changing the saved configuration.
Permission waits expire after 60 seconds. Disconnecting during permission or
connection setup returns a failure instead of reporting the printer ready.

`UsbReadyResult` contains `status`, `message`, `isReady`, and an optional raw
SDK `connectionCode` (0 is a successful SDK connection).

| Status | Application behavior |
| --- | --- |
| `ready` | Send the receipt. |
| `notFound` | Ask the user to power on/connect the printer, then try again. |
| `permissionDenied` | Explain that USB access is required; allow a user-initiated retry. |
| `permissionTimeout` | Let the user retry preparation. |
| `disconnected` | Wait for the printer to reconnect, then prepare again. |
| `busy` | Another USB preparation/permission request is pending. |
| `invalidIdentity` | Check the saved printer identity. |
| `connectionFailed` | Show the error; check the printer and allow a retry. |
| `unavailable` | The plugin is unavailable or has been detached. |

The ready result describes connection setup, not paper/cover status or guaranteed
physical print completion. An unplug can still happen after it returns. Existing
`requestUsbPermission` and `connectUsbPrinter` APIs remain available. Other
connection/print calls during a pending USB preparation return `USB_BUSY`.

`vid:pid` assumes one matching printer is attached. With multiple identical devices,
it cannot distinguish units. Serial-qualified identities require permission to
read the serial number and should not be used for initial permission acquisition.
See `example/lib/home_page.dart` for USB selection and preparation before printing.

### Hardware verification

1. Select a USB printer and print after granting permission.
2. Turn only the printer off/on, leaving the app open; print again. If Android
   requests access, grant it and verify printing proceeds without reopening the app.
3. Deny access, retry, and then grant it. Check that only one request runs at a time.
4. Unplug while the permission dialog is open, and during connection setup; verify
   a failure is reported and the next attempt works after reconnecting.
5. Leave the dialog unanswered for 60 seconds, then retry.
6. Switch from USB to LAN/serial and unplug the USB printer; verify the new
   connection is unaffected.
