# GeoAlarm

App Android (Kotlin + Jetpack Compose, sin layouts XML) de alarmas por geolocalización.
Avisa con sonido, vibración, voz y pantalla completa cuando llegás a una parada o a un lugar.
- Paquete: `io.github.douglasadvaita.geoalarm` · minSdk 26 · targetSdk 34 · 1 sola tabla Room (`alarms`)
- Repositorio privado: `https://github.com/DouglasAdvaita/GeoAlarm` (rama `main`)

## ⇦ REGLA OBLIGATORIA DE RESPALDO

**El usuario no ejecuta respaldos a mano: solo aprieta Run.** El respaldo lo hacés vos, sin
preguntarle, en DOS momentos: **al abrir una sesión de trabajo** y **al cerrarla, si hubo cambios**.

```powershell
$d = "C:\Mis Documentos\_superacion\Codex\respaldos"
New-Item -ItemType Directory -Force -Path $d | Out-Null
$z = Join-Path $d ("GeoAlarm-" + (Get-Date -Format "yyyy-MM-dd_HHmm") + ".zip")
tar -a -c -f $z --exclude=".gradle" --exclude="build" --exclude=".idea" --exclude="*.apk" `
    -C "C:\Users\jorge\AndroidStudioProjects\GeoAlarm" .
Get-ChildItem $d -Filter "GeoAlarm-*.zip" | Sort-Object LastWriteTime -Descending | Select-Object -Skip 5 | Remove-Item -Force
```

Deja solo los 5 respaldos más recientes. Dime en una línea que quedó hecho.
Antes de **modificar** cualquier archivo, hacé el respaldo. Con eso el `git commit` es la segunda capa, no la única.

## Prohibido

- **Nunca desinstalar la app ni pedirle al usuario que la desinstale.** Se borran las alarmas
  (base Room) y no hay otra copia. Compilar e instalar es solo con **Run** de Android Studio:
  eso **conserva los datos**. Para desinstalar hace falta permiso explícito del usuario, siempre.
- No tocar `~/.gitconfig` ni configuración global de git. La identidad va por repo (`git config --local`).
- No hacer `git commit`, `push` ni `checkout` sin que lo pida.

## Pendientes conocidos (verificados 28/09/2026, avance 05/10/2026 chat GeoAlarm-Play)

1. `WatchService` snooze 5 min: corregido 05/10 (`wasOutside[id]=true` al posponer, permite re-sonar dentro del radio). Ver `WatchService.kt.bak`.
2. `BootReceiver.onReceive` vacío: queda así a propósito en v1 (Play restringe auto-arranque en Android 14+). Al reabrir la app se reactiva solo.
3. Canal alarma: sonido agregado 05/10 (`Notifications.kt` + `AudioAttributes.USAGE_ALARM`). Falta: ícono notificación monocromo (hoy `mipmap` sale cuadrado blanco).
4. `AppDatabase` `fallbackToDestructiveMigration()` → borra base en cambio esquema. Pendiente: migración real + `exportSchema=true`. No tocar sin día verde.
5. `MainActivity.autoStart()`: corregido 05/10 a `lifecycleScope` (antes `CoroutineScope` sin cancelar).
6. Switch "Repetir cada vez": `Alarm.repeat` se guarda pero servicio siempre descansa hasta 6am. Pendiente: definir regla (repetir = sin descanso vs una vez).
7. Permisos: `WAKE_LOCK` sacado 05/10 (sin uso). `MODIFY_AUDIO_SETTINGS` queda (sube volumen alarma). `CAMERA` ya no estaba en manifiesto.
8. Base `version=2` `exportSchema=false`: pendiente documentar qué cambió en v2.

## Para Play (memoria 05/10)
- Falta: keystore release (hoy debug), App Bundle, `targetSdk 34→35`, ficha ES+EN, capturas, política privacidad ubicación, Data Safety, rating contenido.
- Cuenta nueva: prueba cerrada 12 testers 14 días antes de producción.
- `Issues` GitHub = lista pública de bugs/ideas. `Proyecto vive` = commits + issues cerrados visibles, no vigilancia. Sin guardia: solo responder cuando llega mail de issue.
