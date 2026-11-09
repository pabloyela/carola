# Carola — reproductor de música para el radio del carro

App Android sencilla para escuchar la música de tus memorias USB en un radio con Android 9 (funciona desde Android 8).

**Qué hace**

- Detecta las USB conectadas al radio (también la memoria interna) y te deja navegar por carpetas.
- Arma una vista de **Álbumes** y **Artistas** leyendo las etiquetas de las canciones.
- **Portadas automáticas**, en este orden: `cover.jpg` / `folder.jpg` de la carpeta → la imagen dentro del MP3/FLAC → descarga desde **MusicBrainz / Cover Art Archive** (gratis, sin registro). Las descargadas se guardan en el radio, solo se descargan una vez.
- Panel de reproducción grande para usar manejando: portada, fondo con los colores del disco, barra de tiempo, aleatorio, repetir y cola.
- Sigue sonando en segundo plano, responde a los **botones del volante** y respeta las indicaciones del GPS (baja o pausa la música).
- Recuerda la última canción y continúa al encender el carro.
- Al conectar una USB se actualiza solo.

---

## 1. Obtener el APK con GitHub (recomendado, sin instalar nada)

1. Crea una cuenta en [github.com](https://github.com) si no tienes.
2. Arriba a la derecha: **+ → New repository**. Ponle nombre `carola`, déjalo **Private** y presiona **Create repository**.
3. En la página del repositorio vacío, toca el enlace **uploading an existing file**.
4. Descomprime `Carola.zip` en tu computadora, abre la carpeta `Carola` y **arrastra todo su contenido** (las carpetas `app`, `gradle`, `.github` y los demás archivos) a la página. Presiona **Commit changes**.
   - Importante: la carpeta `.github` está oculta en algunos sistemas. Si no la ves, en Windows activa *Ver → Elementos ocultos*; en Mac presiona `Cmd + Shift + .`. Sin esa carpeta GitHub no compila.
5. Ve a la pestaña **Actions**. Verás "Compilar APK" ejecutándose (tarda unos 5–8 minutos la primera vez).
6. Cuando tenga la palomita verde, entra a esa ejecución y abajo, en **Artifacts**, descarga **Carola-apk**. Es un ZIP con el archivo `app-debug.apk` adentro.

Cada vez que cambies algo del código y lo subas, GitHub vuelve a generar el APK.

## 2. Instalar en el radio

1. Copia `app-debug.apk` a una memoria USB.
2. En el radio: **Ajustes → Seguridad → Fuentes desconocidas** (o "Instalar apps desconocidas") y permítelo para el explorador de archivos.
3. Abre el explorador de archivos del radio, entra a la USB y toca `app-debug.apk` → **Instalar**.
4. Abre **Carola** y acepta el permiso de almacenamiento.

> Si el radio tiene una opción de "app de música predeterminada" o de qué app abrir con el botón *MEDIA*, puedes elegir Carola ahí.

## 3. Alternativa: Android Studio

Abre la carpeta `Carola` en Android Studio (versión Koala o más reciente), espera a que sincronice y usa **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

---

## Consejos

- **Portadas sin internet**: si pones un `cover.jpg` en cada carpeta de álbum, se usa sin necesidad de descargar nada.
- **Portadas por internet**: conecta el radio al hotspot del teléfono un rato y entra a **Álbumes**; las portadas se van bajando solas mientras recorres la lista. MusicBrainz permite una consulta por segundo, así que con muchos álbumes tarda unos minutos.
- Si una carpeta se llama `Artista - Álbum` y las canciones no tienen etiquetas, se usa ese nombre para buscar la portada.
- Si el radio monta la USB en una ruta rara y no aparece, revísalo en **Ajustes → Almacenamiento detectado** y avísame la ruta para agregarla.

## Estructura del código

| Archivo | Qué hace |
|---|---|
| `data/StorageRoots.kt` | Detecta las USB y lista carpetas/canciones |
| `data/LibraryRepository.kt` | Lee etiquetas (título, artista, álbum) y las guarda en caché |
| `data/CoverRepository.kt` | Busca portadas: carpeta → incrustada → internet |
| `data/MusicBrainzClient.kt` | Consulta MusicBrainz y Cover Art Archive |
| `player/PlaybackService.kt` | Reproductor en segundo plano (Media3/ExoPlayer) |
| `MainViewModel.kt` | Une todo: cola, estado, reanudar, portada actual |
| `ui/*` | Pantallas (Sonando, Carpetas, Álbumes, Artistas, Ajustes) |
