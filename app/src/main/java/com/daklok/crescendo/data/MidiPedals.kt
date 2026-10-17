package com.daklok.crescendo.data

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stlačenie / uvoľnenie pedála klavíra (MIDI Control Change). */
data class PedalEvent(val cc: Int, val pressed: Boolean)

/**
 * Pedále digitálneho klavíra cez MIDI: USB kábel (klavír sa zistí sám) alebo Bluetooth MIDI.
 * Počúva správy Control Change (sustain 64, sostenuto 66, soft 67 …) a posiela udalosti stlačenia.
 */
class MidiPedals(private val context: Context) {

    private val manager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(MidiManager::class.java)
        } else null

    /** Či zariadenie podporuje MIDI vôbec. */
    val supported: Boolean get() = manager != null

    private val handler = Handler(Looper.getMainLooper())
    private val opened = HashMap<Int, Pair<MidiDevice, MidiOutputPort>>()
    private val names = HashMap<Int, String>()
    private val ccDown = BooleanArray(128)
    private var users = 0

    private val _events = MutableSharedFlow<PedalEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PedalEvent> = _events.asSharedFlow()

    /** Názvy pripojených nástrojov. */
    private val _connected = MutableStateFlow<List<String>>(emptyList())
    val connected: StateFlow<List<String>> = _connected.asStateFlow()

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            val end = offset + count
            while (i < end) {
                val status = msg[i].toInt() and 0xFF
                when {
                    status and 0xF0 == 0xB0 && i + 2 < end -> {
                        val cc = msg[i + 1].toInt() and 0x7F
                        val value = msg[i + 2].toInt() and 0x7F
                        val down = value >= 64
                        if (ccDown[cc] != down) {
                            ccDown[cc] = down
                            _events.tryEmit(PedalEvent(cc, down))
                        }
                        i += 3
                    }
                    status == 0xF0 -> {
                        // systémová správa – preskočíme po koniec (0xF7)
                        while (i < end && (msg[i].toInt() and 0xFF) != 0xF7) i++
                        i++
                    }
                    status >= 0xF8 -> i += 1
                    status and 0xF0 == 0xC0 || status and 0xF0 == 0xD0 -> i += 2
                    status >= 0x80 -> i += 3
                    else -> i += 1
                }
            }
        }
    }

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = openDevice(device)
        override fun onDeviceRemoved(device: MidiDeviceInfo) = closeDevice(device.id)
    }

    /** Začne počúvať (volá sa pri otvorení čítačky / učení pedála). Volania sa počítajú. */
    fun start() {
        val m = manager ?: return
        users++
        if (users > 1) return
        m.registerDeviceCallback(callback, handler)
        @Suppress("DEPRECATION")
        m.devices.forEach { openDevice(it) }
    }

    fun stop() {
        val m = manager ?: return
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        runCatching { m.unregisterDeviceCallback(callback) }
        opened.keys.toList().forEach { closeDevice(it) }
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val m = manager ?: return
        if (info.outputPortCount == 0 || opened.containsKey(info.id)) return
        m.openDevice(info, { device -> attach(info, device) }, handler)
    }

    private fun attach(info: MidiDeviceInfo, device: MidiDevice?) {
        if (device == null) return
        if (opened.containsKey(info.id)) {
            // to isté zariadenie prišlo dvakrát (napr. Bluetooth + callback) – druhé zavrieme
            if (opened[info.id]?.first !== device) runCatching { device.close() }
            return
        }
        val portIndex = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }?.portNumber ?: 0
        val port = device.openOutputPort(portIndex) ?: run {
            runCatching { device.close() }
            return
        }
        port.connect(receiver)
        opened[info.id] = device to port
        val props = info.properties
        names[info.id] = props.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI"
        publish()
    }

    private fun closeDevice(id: Int) {
        opened.remove(id)?.let { (device, port) ->
            runCatching { port.disconnect(receiver) }
            runCatching { port.close() }
            runCatching { device.close() }
        }
        names.remove(id)
        publish()
    }

    private fun publish() {
        _connected.value = names.values.toList()
    }

    // ───────────────────── Bluetooth MIDI ─────────────────────

    fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    /** Pripojí Bluetooth MIDI klavír; po pripojení sa objaví medzi nástrojmi. */
    @android.annotation.SuppressLint("MissingPermission")
    fun connectBluetooth(device: BluetoothDevice, onResult: (Boolean) -> Unit = {}) {
        val m = manager ?: return onResult(false)
        if (!hasBluetoothPermission()) return onResult(false)
        runCatching {
            m.openBluetoothDevice(device, { midi ->
                if (midi == null) {
                    onResult(false)
                } else {
                    attach(midi.info, midi)
                    onResult(true)
                }
            }, handler)
        }.onFailure { onResult(false) }
    }

    /** Automatické pripojenie naposledy použitého klavíra podľa adresy. */
    fun connectBluetoothAddress(address: String) {
        if (address.isBlank() || !hasBluetoothPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        connectBluetooth(device)
    }

    companion object {
        /** Služba Bluetooth LE MIDI (štandard). */
        val BLE_MIDI_SERVICE: ParcelUuid = ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")

        /** Ľudský názov pedála podľa čísla Control Change. */
        fun pedalName(cc: Int, sustain: String, sostenuto: String, soft: String): String = when (cc) {
            64 -> sustain
            66 -> sostenuto
            67 -> soft
            else -> "CC $cc"
        }
    }
}
