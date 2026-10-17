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

/** Pripojený nástroj: názov a spôsob pripojenia. */
data class MidiInstrument(val name: String, val bluetooth: Boolean, val virtual: Boolean = false)

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

    /** Pripojené nástroje aj so spôsobom pripojenia (USB / Bluetooth). */
    private val _instruments = MutableStateFlow<List<MidiInstrument>>(emptyList())
    val instruments: StateFlow<List<MidiInstrument>> = _instruments.asStateFlow()
    private val bluetoothIds = HashSet<Int>()
    private val virtualIds = HashSet<Int>()

    /** Používateľ klavír ručne odpojil – kým ho nepripojí znova, nič sa samo nepripája. */
    private val _manualOff = MutableStateFlow(false)
    val manualOff: StateFlow<Boolean> = _manualOff.asStateFlow()

    /** Odpojí všetky nástroje (USB aj Bluetooth) a nechá ich odpojené. */
    fun disconnect() {
        _manualOff.value = true
        opened.keys.toList().forEach { closeDevice(it) }
    }

    /** Znova pripojí: USB nástroje hneď, Bluetooth klavír podľa uloženej adresy. */
    fun reconnect(btAddress: String) {
        _manualOff.value = false
        val m = manager ?: return
        if (users > 0) {
            @Suppress("DEPRECATION")
            m.devices.forEach { openDevice(it) }
        }
        connectBluetoothAddress(btAddress)
    }

    /**
     * Vypnutie Bluetooth / odpojenie zariadenia: Android to pri Bluetooth MIDI nie vždy ohlási,
     * preto zariadenie zavrieme sami – inak by aplikácia ukazovala „pripojené“, hoci nie je.
     */
    private val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            when (intent.action) {
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                    if (state == android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF ||
                        state == android.bluetooth.BluetoothAdapter.STATE_OFF
                    ) {
                        bluetoothIds.toList().forEach { closeDevice(it) }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    @Suppress("DEPRECATION")
                    val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val address = runCatching { dev.address }.getOrNull() ?: return
                    btAddresses.filterValues { it == address }.keys.toList().forEach { closeDevice(it) }
                }
            }
        }
    }
    private var btReceiverOn = false

    /** Čas (uptime ms) poslednej zahranej noty – na „blikajúcu“ kontrolku, že klavír naozaj posiela dáta. */
    private val _activity = MutableStateFlow(0L)
    val activity: StateFlow<Long> = _activity.asStateFlow()

    /** Ďalší poslucháči všetkých správ (napr. nahrávanie hry z klavíra). */
    private val rawListeners = java.util.concurrent.CopyOnWriteArrayList<(ByteArray, Int, Int, Long) -> Unit>()

    fun addListener(l: (ByteArray, Int, Int, Long) -> Unit) {
        rawListeners.add(l)
    }

    fun removeListener(l: (ByteArray, Int, Int, Long) -> Unit) {
        rawListeners.remove(l)
    }

    /** Bluetooth adresy pripojených zariadení (aby sa to isté nepripájalo dvakrát). */
    private val btAddresses = HashMap<Int, String>()

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            for (l in rawListeners) runCatching { l(msg, offset, count, timestamp) }
            // kontrolka aktivity: stačí nájsť note on (aj pri running status je prvá správa so statusom)
            for (k in offset until offset + count) {
                if (msg[k].toInt() and 0xF0 == 0x90) {
                    _activity.value = android.os.SystemClock.uptimeMillis()
                    break
                }
            }
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
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            // nanovo zapojený USB kábel = jasný úmysl pripojiť
            if (device.type == MidiDeviceInfo.TYPE_USB) _manualOff.value = false
            openDevice(device)
        }
        override fun onDeviceRemoved(device: MidiDeviceInfo) = closeDevice(device.id)
    }

    /** Začne počúvať (volá sa pri otvorení čítačky / učení pedála). Volania sa počítajú. */
    fun start() {
        val m = manager ?: return
        users++
        if (users > 1) return
        m.registerDeviceCallback(callback, handler)
        if (!btReceiverOn) {
            val filter = android.content.IntentFilter().apply {
                addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            btReceiverOn = runCatching {
                ContextCompat.registerReceiver(context, btReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }.isSuccess
        }
        @Suppress("DEPRECATION")
        m.devices.forEach { openDevice(it) }
    }

    fun stop() {
        val m = manager ?: return
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        runCatching { m.unregisterDeviceCallback(callback) }
        if (btReceiverOn) {
            runCatching { context.unregisterReceiver(btReceiver) }
            btReceiverOn = false
        }
        opened.keys.toList().forEach { closeDevice(it) }
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val m = manager ?: return
        if (_manualOff.value || info.outputPortCount == 0 || opened.containsKey(info.id)) return
        m.openDevice(info, { device -> attach(info, device) }, handler)
    }

    private fun attach(info: MidiDeviceInfo, device: MidiDevice?) {
        if (device == null) return
        if (_manualOff.value) {
            runCatching { device.close() }
            return
        }
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
        val product = props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
        val maker = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
        names[info.id] = props.getString(MidiDeviceInfo.PROPERTY_NAME)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(maker, product).joinToString(" ").takeIf { it.isNotBlank() }
            ?: "MIDI"
        if (info.type == MidiDeviceInfo.TYPE_BLUETOOTH) bluetoothIds.add(info.id) else bluetoothIds.remove(info.id)
        if (info.type == MidiDeviceInfo.TYPE_VIRTUAL) virtualIds.add(info.id) else virtualIds.remove(info.id)
        publish()
    }

    private fun closeDevice(id: Int) {
        opened.remove(id)?.let { (device, port) ->
            runCatching { port.disconnect(receiver) }
            runCatching { port.close() }
            runCatching { device.close() }
        }
        names.remove(id)
        btAddresses.remove(id)
        bluetoothIds.remove(id)
        virtualIds.remove(id)
        publish()
    }

    private fun publish() {
        _connected.value = names.values.toList()
        _instruments.value = names.map { (id, name) -> MidiInstrument(name, id in bluetoothIds, id in virtualIds) }
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
        _manualOff.value = false // výber klavíra v zozname = chce sa pripojiť
        runCatching {
            m.openBluetoothDevice(device, { midi ->
                if (midi == null) {
                    onResult(false)
                } else {
                    attach(midi.info, midi)
                    if (opened.containsKey(midi.info.id)) btAddresses[midi.info.id] = device.address
                    onResult(true)
                }
            }, handler)
        }.onFailure { onResult(false) }
    }

    /** Automatické pripojenie naposledy použitého klavíra podľa adresy. */
    fun connectBluetoothAddress(address: String) {
        if (address.isBlank() || !hasBluetoothPermission()) return
        if (btAddresses.containsValue(address)) return // už je pripojený
        if (_manualOff.value) return // ručne odpojený
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
