package com.avatarsolution.printer_module

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** Adapts legacy SDK receiver registration to Android 13+ requirements. */
class ReceiverCompatContext(base: Context) : ContextWrapper(base) {
    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
    ): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        super.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("DEPRECATION")
        super.registerReceiver(receiver, filter)
    }
}
