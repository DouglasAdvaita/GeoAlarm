package io.github.douglasadvaita.geoalarm.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import io.github.douglasadvaita.geoalarm.data.Alarm
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import io.github.douglasadvaita.geoalarm.util.BusAlertPrefs
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@SuppressLint("MissingPermission")
@Composable
fun AddEditScreen(vm: AlarmViewModel, editId: Long?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Alarm.TYPE_BUS) }
    var radius by remember { mutableIntStateOf(Alarm.DEFAULT_BUS_RADIUS) }
    var radiusTxt by remember { mutableStateOf(Alarm.DEFAULT_BUS_RADIUS.toString()) }
    var repeat by remember { mutableStateOf(false) }
    var gpsOn by remember { mutableStateOf(true) }
    // Montevideo por defecto hasta ubicar
    var lat by remember { mutableDoubleStateOf(-34.9011) }
    var lng by remember { mutableDoubleStateOf(-56.1645) }
    var loaded by remember { mutableStateOf(editId == null || editId == 0L) }
    var centered by remember { mutableStateOf(false) }

    // Config global Bus: vale para todas hasta que otro Bus la cambie (solo Bus)
    var busCfg by remember { mutableStateOf(BusAlertPrefs.load(ctx)) }
    val tonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION") res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? android.net.Uri
            }
            val c = busCfg.copy(ringtoneUri = uri?.toString())
            busCfg = c
            BusAlertPrefs.save(ctx, c)
        }
    }
    fun persistBus(c: BusAlertPrefs.Cfg) {
        busCfg = c
        BusAlertPrefs.save(ctx, c)
    }

    fun applyType(t: String) {
        type = t
        if (t == Alarm.TYPE_BUS) {
            radius = Alarm.DEFAULT_BUS_RADIUS
            radiusTxt = Alarm.DEFAULT_BUS_RADIUS.toString()
        } else {
            radius = Alarm.DEFAULT_REMINDER_RADIUS
            radiusTxt = Alarm.DEFAULT_REMINDER_RADIUS.toString()
        }
    }

    LaunchedEffect(Unit) {
        busCfg = BusAlertPrefs.load(ctx)
        // GPS: solo aviso texto
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {}
        // Centra mapa en mi posición SOLO al crear nuevo (editar muestra el punto vigilado)
        val isNew = editId == null || editId == 0L
        if (isNew && !centered) {
            try {
                LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                    .addOnSuccessListener { loc: Location? ->
                        if (loc != null) { lat = loc.latitude; lng = loc.longitude }
                        centered = true
                    }
                    .addOnFailureListener { centered = true }
            } catch (_: SecurityException) { centered = true }
        }
    }

    LaunchedEffect(editId) {
        if (editId != null && editId > 0) {
            val a = AppDatabase.get(ctx).alarmDao().getById(editId)
            if (a != null) {
                name = a.name; message = a.message; type = a.type
                radius = a.radiusMeters; radiusTxt = a.radiusMeters.toString()
                lat = a.lat; lng = a.lng; repeat = a.repeat
                centered = true
            }
            loaded = true
        }
    }

    if (!loaded) {
        Text("Cargando…", Modifier.padding(16.dp))
        return
    }

    val isBus = type == Alarm.TYPE_BUS
    val sliderMin = if (isBus) 50f else 30f
    val sliderMax = if (isBus) 2000f else 200f
    val sliderVal = radius.toFloat().coerceIn(sliderMin, sliderMax)

    // Todo el formulario con scroll para que entre en cualquier celular
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre (ej. Parada Facultad)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Nota (ej. comprar X, traer Y)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = { applyType(Alarm.TYPE_BUS) }) {
                Text(if (isBus) "[x] Bus" else "[ ] Bus")
            }
            TextButton(onClick = { applyType(Alarm.TYPE_REMINDER) }) {
                Text(if (!isBus) "[x] Nota" else "[ ] Nota")
            }
        }
        if (!gpsOn) {
            Text(
                "GPS apagado: prende el GPS para ubicarte.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
        }
        // Radio: slider reescribe texto al moverse; al guardar vale el texto
        Text("Radio: $radiusTxt m", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = sliderVal,
            onValueChange = {
                radius = it.toInt()
                radiusTxt = it.toInt().toString()
            },
            valueRange = sliderMin..sliderMax,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = radiusTxt,
            onValueChange = { v ->
                val digits = v.filter { it.isDigit() }.take(4)
                radiusTxt = digits
                digits.toIntOrNull()?.let { radius = it }
            },
            label = { Text("Radio en m (solo números)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repetir cada vez", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = repeat, onCheckedChange = { repeat = it })
        }
        if (isBus) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Alarma Bus (vale para todas)", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Tono: " + (busCfg.ringtoneUri?.takeLast(20) ?: "defecto"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = {
                        val cur = busCfg.ringtoneUri?.let { android.net.Uri.parse(it) }
                            ?: BusAlertPrefs.defaultAlarmUri(ctx)
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono Bus")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur)
                        }
                        tonePicker.launch(i)
                    }) { Text("Elegir tono", style = MaterialTheme.typography.bodySmall) }
                    BusSwitch("Volumen máximo", busCfg.boostVolume) { persistBus(busCfg.copy(boostVolume = it)) }
                    BusSwitch("Vibración SOS", busCfg.strongVib) { persistBus(busCfg.copy(strongVib = it)) }
                    BusSwitch("Voz parada", busCfg.tts) { persistBus(busCfg.copy(tts = it)) }
                    BusSwitch("3 toques p/apagar", busCfg.tripleConfirm) { persistBus(busCfg.copy(tripleConfirm = it)) }
                    BusSwitch("Avisos por cercanía", busCfg.proximity) { persistBus(busCfg.copy(proximity = it)) }
                }
            }
        }
        Text("Toca el mapa para mover el punto.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        // Mapa 80% del ancho, centrado, cuadrado con recorte
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val side = maxWidth * 0.8f
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OsmPicker(
                    modifier = Modifier.width(side).height(side).clipToBounds(),
                    lat = lat, lng = lng, radius = radius,
                    onPick = { la, ln -> lat = la; lng = ln }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = {
                try {
                    val fused = LocationServices.getFusedLocationProviderClient(ctx)
                    fused.lastLocation.addOnSuccessListener { loc: Location? ->
                        if (loc != null) { lat = loc.latitude; lng = loc.longitude }
                    }
                } catch (_: SecurityException) {}
            }) { Text("Usar mi posición") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = name.isNotBlank(),
            onClick = {
                val r = radiusTxt.toIntOrNull()?.coerceIn(30, 9999) ?: 30
                scope.launch {
                    vm.save(name, message, lat, lng, r, type, repeat, editId = editId) { onDone() }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Guardar") }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BusSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OsmPicker(modifier: Modifier = Modifier, lat: Double, lng: Double, radius: Int, onPick: (Double, Double) -> Unit) {
    val ctx = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }

    AndroidView(
        modifier = modifier,
        factory = { c: Context ->
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(lat, lng))
                // Un dedo mueve el mapa sin scrollear el formulario
                setOnTouchListener { v, e ->
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false // deja que osmdroid procese el gesto
                }
                mapView = this
            }
        },
        update = { mv ->
            mv.overlays.clear()
            val p = GeoPoint(lat, lng)
            // No forzar setCenter acá: deja ver el punto vigilado + círculo sin saltar.
            // El centrado lo hace LaunchedEffect(lat, lng) solo cuando cambia el punto.
            val marker = Marker(mv).apply {
                position = p
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Destino"
            }
            val circle = Polygon().apply {
                points = Polygon.pointsAsCircle(p, radius.toDouble())
                fillColor = 0x335577FF
                strokeColor = 0xFF5577FF.toInt()
                strokeWidth = 3f
            }
            mv.overlays.add(circle)
            mv.overlays.add(marker)
            mv.invalidate()
            val tapOverlay = object : org.osmdroid.views.overlay.Overlay() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                    if (e != null && mapView != null) {
                        val proj = mapView.projection
                        val gp = proj.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                        onPick(gp.latitude, gp.longitude)
                        return true
                    }
                    return super.onSingleTapConfirmed(e, mapView)
                }
            }
            mv.overlays.add(tapOverlay)
        },
        onRelease = { mv -> mv.onDetach() }
    )
    androidx.compose.runtime.LaunchedEffect(lat, lng) {
        mapView?.controller?.setCenter(GeoPoint(lat, lng))
        mapView?.invalidate()
    }
    androidx.compose.runtime.LaunchedEffect(ctx) {}
}
