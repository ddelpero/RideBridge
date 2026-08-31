package com.ddelpero.ridebridge

// import android.content.Context
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ddelpero.ridebridge.communication.Manager
import com.ddelpero.ridebridge.core.MediaManager
import com.ddelpero.ridebridge.core.Settings as RideBridgeSettings

class MainActivity : AppCompatActivity() {
    private var isReceiverRegistered = false

    private var isTabletMode = false
    private lateinit var roleSwitch: SwitchCompat
    private lateinit var autoStartSwitch: SwitchCompat
    private lateinit var transportSwitch: SwitchCompat
    private lateinit var roleLableText: TextView
    private lateinit var btnStart: Button
    private lateinit var logView: TextView

    private var widgetContainer: FrameLayout? = null
    private var currentWidgetView: View? = null

    private var isStarted = false

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
        }
    }

    private fun checkAndRequestPermissions() {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = flat?.contains(pkgName) == true

        if (!isEnabled) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        needed.add(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestBluetoothPermissions.launch(missing.toTypedArray())
        }
    }

    private fun updateSelectTabletVisibility(isTablet: Boolean) {
        findViewById<Button>(R.id.btn_select_tablet).visibility =
            if (isTablet) View.GONE else View.VISIBLE
    }

    private fun updateRoleLabel(isTabletMode: Boolean) {
        roleLableText.text = if (isTabletMode) "This device is the tablet" else "This device is the phone"
    }

    private fun showDashboardMediaPlayer() {
        val dashboard = findViewById<View>(R.id.dashboard_media_player)
        dashboard?.visibility = View.VISIBLE
    }

    private fun updateStateButtonText(isRunning: Boolean) {
        val startButton = findViewById<Button>(R.id.btnStart)
        startButton.text = if (isRunning) getString(R.string.stop_service) else getString(R.string.start_service)
        if (isRunning) {
            startButton.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.rb_stop))
            startButton.setTextColor(ContextCompat.getColor(this, R.color.rb_on_stop))
        } else {
            startButton.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.rb_primary))
            startButton.setTextColor(ContextCompat.getColor(this, R.color.rb_on_primary))
        }

        val chip = findViewById<TextView>(R.id.statusChip)
        if (isRunning) {
            chip.text = getString(R.string.status_running)
            chip.setBackgroundResource(R.drawable.bg_chip_running)
            chip.setTextColor(ContextCompat.getColor(this, R.color.rb_chip_running_text))
        } else {
            chip.text = getString(R.string.status_stopped)
            chip.setBackgroundResource(R.drawable.bg_chip_stopped)
            chip.setTextColor(ContextCompat.getColor(this, R.color.rb_text_muted))
        }
    }

    private fun updateTransportLabel(isEmulator: Boolean) {
        val transportLabel = findViewById<TextView>(R.id.transportLabelText)
        transportLabel.text = if (isEmulator) "TCP (emulator)" else "Bluetooth"
    }

    // @SuppressLint("MissingPermission")
    private fun openDevicePicker() {
        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            val missing = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 101)
                return // Added return so we don't hit bondedDevices without permission
            } else {
                Log.d("Settings", "Permissions already granted.")
            }
        }

        // Linter is now happy because of @SuppressLint
        val pairedDevices = adapter.bondedDevices.toList()

        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired devices found.", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: "Unknown Device" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Tablet")
            .setItems(deviceNames) { _, which ->
                val device = pairedDevices[which]

                getSharedPreferences("RideBridgePrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("tablet_mac_address", device.address)
                    .apply()

                Toast.makeText(this, "Target set to: ${device.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDeviceAddress(address: String) {
        val prefs = getSharedPreferences("RideBridgePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("tablet_mac_address", address).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)
        val padLeft = root.paddingLeft
        val padTop = root.paddingTop
        val padRight = root.paddingRight
        val padBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                padLeft + systemBars.left,
                padTop + systemBars.top,
                padRight + systemBars.right,
                padBottom + systemBars.bottom
            )
            insets
        }

        val trackName = findViewById<TextView>(R.id.widget_track)
        val artistName = findViewById<TextView>(R.id.widget_artist)
        val albumArt = findViewById<android.widget.ImageView>(R.id.widget_album_art)
        val playPause = findViewById<android.widget.ImageButton>(R.id.widget_play_pause)
        val statusIcon = findViewById<android.widget.ImageView>(R.id.widget_connection_status)

        findViewById<Button>(R.id.btn_select_tablet).setOnClickListener {
            openDevicePicker()
        }
        playPause.setOnClickListener {
            Log.d("DashboardClick", "POC: Play/Pause button pressed on Dashboard!")
            Manager.sendCommandToClient("PLAY")
        }

        MediaManager.liveData.observe(this) { state ->
            // This code runs every time the Manager gets new data
            trackName.text = state.track
            artistName.text = state.artist

            if (state.albumArtBitmap != null) {
                albumArt.setImageBitmap(state.albumArtBitmap)
            }

            var playButtonRes = android.R.drawable.ic_media_play
            if (state.isPlaying) {
                playButtonRes = android.R.drawable.ic_media_pause
            }
            playPause.setImageResource(playButtonRes)

        }

        Manager.connectionStatus.observe(this) { isConnected ->
            if (isConnected) {
                statusIcon.setImageResource(R.drawable.ic_circle_green)
            } else {
                statusIcon.setImageResource(R.drawable.ic_circle_red)
            }
        }

        transportSwitch = findViewById(R.id.transportSwitch)
        val isEmulator = RideBridgeSettings.isEmulator(this)
        transportSwitch.isChecked = isEmulator
        updateTransportLabel(isEmulator)
        transportSwitch.setOnCheckedChangeListener { _, isCheckedb ->
            RideBridgeSettings.saveEmulator(this, isCheckedb)
            updateTransportLabel(isCheckedb)
        }

        autoStartSwitch = findViewById(R.id.autoStartSwitch)
        val isAutoStart = RideBridgeSettings.isAutoStart(this)
        autoStartSwitch.isChecked = isAutoStart

        autoStartSwitch.setOnCheckedChangeListener { _, isCheckedb ->
            RideBridgeSettings.saveAutoStart(this, isCheckedb)
        }

        roleLableText = findViewById(R.id.roleLabelText)

        roleSwitch = findViewById(R.id.roleSwitch)
        isTabletMode = RideBridgeSettings.isTablet(this)
        roleSwitch.isChecked = isTabletMode
        updateSelectTabletVisibility(isTabletMode)

        if (isAutoStart) {
            Manager.start(this, isTabletMode, isEmulator)
            updateStateButtonText(true)
        } else {
            updateStateButtonText(false)
        }

        updateRoleLabel(isTabletMode)
        if (isTabletMode) {
            showDashboardMediaPlayer()
        }

        roleSwitch.setOnCheckedChangeListener { _, isChecked ->
            // code for the background service goes here?
            this.isTabletMode = isChecked
            updateRoleLabel(isChecked)
            RideBridgeSettings.saveRole(this, isChecked)
            updateSelectTabletVisibility(isChecked)
        }

        logView = findViewById(R.id.logView)

        btnStart = findViewById(R.id.btnStart)

        btnStart.setOnClickListener {
            // Todo: start server
            if (Manager.isRunning) {
                logView.setText("Stopping...\n" + logView.text)
                updateStateButtonText(false)
                Manager.stop()
            } else {
                logView.setText("Starting...\n" + logView.text)
                updateStateButtonText(true)
                Manager.start(this, isTabletMode, isEmulator)
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
    }
}