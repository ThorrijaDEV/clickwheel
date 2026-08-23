# Clickwheel · Console

Two players, one idea: music you *operate*, not just tap.

| | [android/](android/) — **Clickwheel** | [web/](web/) — **Console** |
|---|---|---|
| Plataforma | Android (Kotlin + Jetpack Compose) | Navegador (vanilla JS, cero dependencias) |
| Estética | iPod clásico: pantalla LCD y click-wheel real con háptica | Equipo hi-fi vintage: madera, ámbar y VU-meter |
| Música local | MediaStore del dispositivo | Archivos de tu equipo vía file picker |
| YouTube Music | Búsqueda + streaming vía NewPipeExtractor | — (los navegadores no pueden extraer audio de YouTube) |

## Clickwheel (Android)

Un reproductor que se maneja como un iPod de 2004:

- **Click-wheel funcional**: arrastra alrededor del aro para navegar (un tick por paso, con vibración), botones cardinales `MENU / ⏮ / ⏭ / ▶` y botón central para seleccionar.
- **Biblioteca local**: lee el audio del dispositivo vía `MediaStore`, sin escaneos raros ni permisos excesivos.
- **YouTube Music**: búsqueda de canciones y reproducción en streaming usando [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — la misma capa comunitaria de extracción que usa NewPipe. No hay API oficial de YTM; esto es ingeniería inversa mantenida por la comunidad y **puede romperse** si YouTube cambia algo.

### Compilar

Requiere JDK 17 y un Android SDK (API 34):

```bash
cd android
echo "sdk.dir=/ruta/a/android-sdk" > local.properties
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Instala el APK en tu teléfono y concede el permiso de audio al abrir.

### Limitaciones v0.1

- Sin reproducción en segundo plano (media session service) todavía.
- El stream de YTM se resuelve al vuelo; sin caché offline.
- La extracción de YouTube es inherentemente frágil y zona gris de sus ToS — este proyecto no aloja ni descifra contenido, solo usa la biblioteca extractor estándar de la comunidad.

Roadmap: media-session + widget, cola editable desde la rueda, caché opcional, temas de carcasa (¿alguien dijo u2 special edition?).

## Console (Web)

El mismo concepto de "reproductor operable", traducido a otro idioma visual: una consola hi-fi de los 70. Cargas una carpeta de tu disco y todo sucede **en tu navegador** — ningún byte sale de tu equipo.

- Cola con shuffle (`⤨`) y repeat (off/all/one)
- Timeline, volumen persistente, VU-meter animado
- Atajos: `Espacio` play/pausa · `←`/`→` pista · `S` aleatorio · `R` repetir

Sin build, sin dependencias, sin cuentas: abre `index.html` o sírvelo estático.

```bash
cd web && python3 -m http.server 8080
```

> Los navegadores no permiten extraer audio de YouTube (CORS/DRM), así que Console es deliberadamente solo-local. Para YTM en web haría falta un servidor companion — ver roadmap.

## Estructura

```
clickwheel/
├── android/          # app Kotlin/Compose
│   └── app/src/main/java/dev/thorrija/clickwheel/
│       ├── ClickWheel.kt       # la rueda: gestos + háptica + botones
│       ├── MainActivity.kt     # navegación entre pantallas
│       ├── Screens.kt          # menú LCD, listas, búsqueda, now playing
│       ├── PlayerViewModel.kt  # ExoPlayer + estado
│       ├── MediaLibrary.kt     # MediaStore scan
│       └── Ytm.kt              # NewPipeExtractor: búsqueda + stream
└── web/              # Console: index.html + style.css + app.js
```

## Licencia

[MIT](LICENSE) — excepto lo que corresponda a las bibliotecas usadas (NewPipeExtractor, OkHttp, AndroidX), cada una con su propia licencia.
