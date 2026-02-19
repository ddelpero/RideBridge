package com.ddelpero.ridebridge

// import android.content.Context
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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


    private val requestMediaLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) { /* Media Access Granted */
        }
    }

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMediaLauncher.launch(permission)
        }

        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = flat?.contains(pkgName) == true

        if (!isEnabled) {
            // This permission CANNOT show a popup; you must send the user to Settings
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestMediaLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateRoleLabel(isTabletMode: Boolean) {
        roleLableText.text = if (isTabletMode) "Tablet" else "Phone"
    }

    private fun showDashboardMediaPlayer() {
        val dashboard = findViewById<View>(R.id.dashboard_media_player)
        dashboard?.visibility = View.VISIBLE
    }

    private fun updateStateButtonText(isRunning: Boolean) {
        val startButton = findViewById<Button>(R.id.btnStart)
        val text = if (isRunning) "Stop Service" else "Start Service"
        startButton.setText(text)
    }

    private fun updateTransportLabel(isEmulator: Boolean) {
        val transportLabel = findViewById<TextView>(R.id.transportLabelText)
        val text = if (isEmulator) "TCP" else "BlueTooth"
        transportLabel.setText(text)
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val trackName = findViewById<TextView>(R.id.widget_track)
        val artistName = findViewById<TextView>(R.id.widget_artist)
        val albumArt = findViewById<android.widget.ImageView>(R.id.widget_album_art)
        val playPause = findViewById<android.widget.ImageButton>(R.id.widget_play_pause)
        val statusIcon = findViewById<android.widget.ImageView>(R.id.widget_connection_status)
        val voiceAssist = findViewById<android.widget.ImageButton>(R.id.widget_voice_command)

        findViewById<Button>(R.id.btn_select_tablet).setOnClickListener {
            openDevicePicker()
        }
        playPause.setOnClickListener {
            Log.d("DashboardClick", "POC: Play/Pause button pressed on Dashboard!")
            Manager.sendCommandToClient("PLAY")
            // Later, you'll call CommunicationManager.sendPlayPause() here
        }

        voiceAssist.setOnClickListener {
            Log.d("DashboardClick", "POC: voiceAssist button pressed on Dashboard!")
            Manager.sendCommandToClient("VOICE_ASSIST")
            // Later, you'll call CommunicationManager.sendPlayPause() here
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
        updateTransportLabel(isEmulator)
        transportSwitch.setOnCheckedChangeListener { _, isCheckedb ->
            RideBridgeSettings.saveEmulator(this, isCheckedb)
        }

        autoStartSwitch = findViewById(R.id.autoStartSwitch)
        val isAutoStart = RideBridgeSettings.isAutoStart(this)

        autoStartSwitch.setOnCheckedChangeListener { _, isCheckedb ->
            RideBridgeSettings.saveAutoStart(this, isCheckedb)
        }

        roleLableText = findViewById(R.id.roleLabelText)

        roleSwitch = findViewById(R.id.roleSwitch)
        isTabletMode = RideBridgeSettings.isTablet(this)
        roleSwitch.isChecked = isTabletMode

        if (isAutoStart) {
            // TODO: start
            autoStartSwitch.isChecked = true

            Manager.start(this, isTabletMode, isEmulator)
            updateStateButtonText(true)
        }

        if (isTabletMode) {
            // Update role label
            updateRoleLabel(isTabletMode)
            showDashboardMediaPlayer()
        }

        roleSwitch.setOnCheckedChangeListener { _, isChecked ->
            // code for the background service goes here?
            this.isTabletMode = isChecked
            updateRoleLabel(isChecked)
            RideBridgeSettings.saveRole(this, isChecked)
        }

        logView = findViewById(R.id.logView)

        btnStart = findViewById(R.id.btnStart)
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