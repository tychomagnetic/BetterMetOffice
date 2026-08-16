package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetAutoRefreshReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received action: $action")

        when (action) {
            WidgetRefreshManager.ACTION_AUTO_REFRESH -> {
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        WidgetRefreshManager.performWidgetRefresh(context.applicationContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WidgetRefreshManager.scheduleAutoRefresh(context.applicationContext)
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        WidgetRefreshManager.performWidgetRefresh(context.applicationContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "WidgetAutoRefreshReceiver"
    }
}
