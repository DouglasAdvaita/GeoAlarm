# GeoAlarm

App Android de alarmas por geolocalización (Kotlin + Jetpack Compose).
Avisa con sonido, vibración y pantalla completa cuando llegás a una parada o lugar.

---

## Cómo se actualiza la app instalada (dinámica de publicación)

La app **no** busca el APK en los archivos del repositorio. Lo busca en los
**Releases** de GitHub.

El código que lo hace está en
`app/src/main/java/io/github/douglasadvaita/geoalarm/util/UpdateChecker.kt`:

1. Llama a `https://api.github.com/repos/DouglasAdvaita/GeoAlarm/releases/latest`.
2. Toma el **release más reciente** (no borrador, no prerelease).
3. Busca entre sus adjuntos el primer archivo terminado en `.apk`
   (`assets[].browser_download_url`).
4. Calcula el `versionCode` del release: primero lee `versionCode: N` del
   **cuerpo** del release; si no, usa el tag.
5. Lo compara con el `versionCode` instalado en el teléfono. Si es **mayor**,
   ofrece «Buscar actualización» → descarga el APK → lo instala.

**Conclusión:** publicar = **crear un Release en GitHub con el APK adjunto como
asset**. No alcanza con commitear el APK al repositorio.

## Cómo publicar una versión nueva

1. Construir el APK con **Run** en Android Studio (conserva los datos del teléfono).
2. En GitHub: repo `DouglasAdvaita/GeoAlarm` → **Releases** → **Draft a new release**.
3. **Tag** nuevo (ej. `v7`), distinto de los tags existentes.
4. **Título**: `GeoAlarm 1.6`.
5. **Cuerpo** (la app lee el versionCode de acá):
   ```
   versionCode: 7
   versionName 1.6
   ...notas de la versión...
   ```
6. **Adjuntar** el APK como asset (ej. `geoalarm-v7.apk`).
   Ruta del build de Run:
   `C:\Users\jorge\AndroidStudioProjects\GeoAlarm\app\build\outputs\apk\debug\app-debug.apk`
7. **Publish release** (no dejarlo como draft ni prerelease).

Después, en el teléfono: Ajustes → **Buscar actualización** → descarga e instala.
La actualización solo aparece en teléfonos cuyo `versionCode` instalado sea
**menor** que el del release.

## Estado actual (23/11/2026)

- Último release público: **v7** (`versionCode 7`, `versionName 1.6`), con **Trotar**.
  Asset: `geoalarm-v7.apk`.
- Los teléfonos con `versionCode` menor que 7 ya ven «Buscar actualización» e instalan Trotar.
- APK que se publicó (build de Run):
  `C:\Users\jorge\AndroidStudioProjects\GeoAlarm\app\build\outputs\apk\debug\app-debug.apk`
