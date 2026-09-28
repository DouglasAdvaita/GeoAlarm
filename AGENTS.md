# GeoAlarm

App Android (Kotlin + Jetpack Compose, sin layouts XML) de alarmas por geolocalización.
Avisa con sonido, vibración, voz y pantalla completa cuando llegás a una parada o a un lugar.
- Paquete: `io.github.douglasadvaita.geoalarm` · minSdk 26 · targetSdk 34 · 1 sola tabla Room (`alarms`)
- Último commit de F0: repositorio `main`, autor local `Jorge <jorge@local>`, **sin remoto** (nada sale de la máquina)

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

## Pendientes conocidos (verificados 28/09/2026)

1. `WatchService`: al posponer 5 min, `wasOutside[id] = false` hace que la alarma **no vuelva a sonar**
2. `BootReceiver.onReceive` está **vacío**: reiniciar el celu deja de vigilar hasta abrir la app
3. El canal de notificación de la alarma no tiene **sonido** (solo vibración) y usa un ícono
   adaptativo de app → **cuadrado blanco** en la barra
4. `AppDatabase` usa `fallbackToDestructiveMigration()` → **borra la base** en cada cambio de esquema
5. `MainActivity.autoStart()` crea un `CoroutineScope` sin cancelar y se llama en onCreate y onResume
6. El switch "Repetir cada vez" no hace nada: `Alarm.repeat` se guarda y el servicio nunca lo lee
7. `MODIFY_AUDIO_SETTINGS` está declarado y sí se usa; `CAMERA` (flash) y `WAKE_LOCK` están declarados
   y **no se usan** → son permisos que sobran y hacen ruido en la tienda
8. La base está en `version = 2` con `exportSchema = false`: no hay registro de qué cambió en v2
