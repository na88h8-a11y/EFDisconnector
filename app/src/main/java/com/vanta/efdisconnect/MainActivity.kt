package com.vanta.efdisconnect

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity — one button, two states.
 *
 * CONNECTED  → red button   → tap → request VPN permission → start BlockVpnService
 * BLOCKED    → green button → tap → stop BlockVpnService → eFootball reconnects
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val VPN_REQUEST_CODE = 100
    }

    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvSubtitle: TextView

    private var isBlocking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle  = findViewById(R.id.btnToggle)
        tvStatus   = findViewById(R.id.tvStatus)
        tvSubtitle = findViewById(R.id.tvSubtitle)

        btnToggle.setOnClickListener {
            if (isBlocking) {
                stopBlocking()
            } else {
                requestVpnAndStart()
            }
        }

        updateUI(blocking = false)
    }

    // ── VPN permission flow ────────────────────────────────────────────────────

    private fun requestVpnAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // System will show the "VPN connection request" dialog
            @Suppress("DEPRECATION")
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            // Permission already granted
            startBlocking()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startBlocking()
        }
        // If user denied: do nothing, button stays in connected state
    }

    // ── Service control ────────────────────────────────────────────────────────

    private fun startBlocking() {
        val intent = Intent(this, BlockVpnService::class.java).apply {
            action = BlockVpnService.ACTION_START
        }
        startService(intent)
        updateUI(blocking = true)
    }

    private fun stopBlocking() {
        val intent = Intent(this, BlockVpnService::class.java).apply {
            action = BlockVpnService.ACTION_STOP
        }
        startService(intent)
        updateUI(blocking = false)
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private fun updateUI(blocking: Boolean) {
        isBlocking = blocking
        if (blocking) {
            btnToggle.text     = "إعادة الاتصال"
            btnToggle.setBackgroundColor(getColor(R.color.green_reconnect))
            tvStatus.text      = "eFootball محجوب"
            tvSubtitle.text    = "الماتش مش بيسجّل — اضغط لما تخلص"
        } else {
            btnToggle.text     = "قطع الاتصال"
            btnToggle.setBackgroundColor(getColor(R.color.red_disconnect))
            tvStatus.text      = "متصل"
            tvSubtitle.text    = "اضغط لما تحس إنك هتخسر"
        }
    }
}
