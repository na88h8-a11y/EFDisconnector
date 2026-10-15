package com.vanta.efdisconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * BlockVpnService — creates a dead-end VPN tunnel for eFootball only.
 *
 * How it works:
 *   1. We call addAllowedApplication("com.konami.pesam") on the VPN builder.
 *      Android then routes ONLY eFootball's traffic through our VPN interface.
 *      Every other app continues using the real network normally.
 *   2. We establish the tunnel interface but never read from it.
 *      Packets eFootball sends go into the tunnel and are never forwarded — black hole.
 *   3. eFootball sees a connection timeout → match scores as error / no-result.
 */
class BlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.vanta.efdisconnect.START"
        const val ACTION_STOP  = "com.vanta.efdisconnect.STOP"

        // eFootball package names — covers all regional variants
        private val EFOOTBALL_PACKAGES = listOf(
            "com.konami.pesam",        // Global / most common
            "com.konami.efootball",    // Alternate package used in some regions
            "jp.konami.pesam"          // Japan region
        )

        private const val CHANNEL_ID   = "ef_disconnector"
        private const val NOTIF_ID     = 1
        private const val TAG          = "BlockVpnService"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                startVpn()
                START_STICKY
            }
        }
    }

    override fun onRevoke() {
        // Called by system when VPN permission is revoked
        stopVpn()
    }

    // ── Core logic ─────────────────────────────────────────────────────────────

    private fun startVpn() {
        if (vpnInterface != null) return  // Already running

        val builder = Builder()
            .setSession("EF Disconnector")
            // Virtual address for our tunnel interface — any unused local address works
            .addAddress("10.255.255.1", 32)
            // Route all IPv4 traffic into the tunnel (for the allowed app only)
            .addRoute("0.0.0.0", 0)
            // Route all IPv6 too
            .addRoute("::", 0)
            // Provide a working DNS so DNS queries don't stall
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")

        // Add every known eFootball package — skip silently if not installed
        var addedAny = false
        for (pkg in EFOOTBALL_PACKAGES) {
            try {
                builder.addAllowedApplication(pkg)
                addedAny = true
                Log.d(TAG, "Blocking: $pkg")
            } catch (e: Exception) {
                // Package not installed — skip
                Log.d(TAG, "Not found: $pkg")
            }
        }

        if (!addedAny) {
            Log.e(TAG, "eFootball not installed — nothing to block")
            stopSelf()
            return
        }

        // Establish the tunnel; never read from it → packets die here
        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface")
            stopSelf()
            return
        }

        Log.d(TAG, "VPN tunnel active — eFootball is isolated")
        showNotification()
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interface: ${e.message}")
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN stopped — eFootball reconnected")
    }

    // ── Notification (required for foreground service) ─────────────────────────

    private fun showNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EF Disconnector",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BlockVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("eFootball محجوب")
            .setContentText("اضغط إيقاف لإعادة الاتصال")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .addAction(android.R.drawable.ic_media_play, "إيقاف الحجب", stopIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }
}
