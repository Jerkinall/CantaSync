# CantaSync
🎵 Mobile app to search and download song lyrics from multiple sources. Early development, debug builds only.

# 🎵 CantaSync

Aplicación nativa para Android que busca **letras sincronizadas por línea** en LRCLIB. Permite seguir la canción que está sonando y guardar la letra como archivo `.lrc`.

> ⚠️ **Estado:** prototipo en desarrollo. La versión actual se ha probado en un dispositivo Android; puede contener errores y todavía no es un lanzamiento estable.

## ✨ Funciones actuales

- 🔎 Busca letras sincronizadas por título y artista.
- ✏️ Permite editar el título y el artista antes de buscar, para probar otras coincidencias.
- 🎼 Muestra las versiones encontradas para que puedas elegir la correcta.
- 🎤 Detecta el título y el artista de la canción activa en un reproductor compatible, si habilitas el acceso a notificaciones de Android.
- ⏱️ Resalta y desplaza la vista hasta la línea correspondiente al progreso de reproducción.
- 💾 Permite guardar la letra elegida como archivo `.lrc`.

La sincronización disponible es **por línea**, no palabra por palabra. Para resaltar la línea actual, CantaSync necesita acceso al reproductor activo. La búsqueda manual funciona sin habilitar ese acceso.

## 🌐 Fuente de letras

Actualmente, la app consulta únicamente:

- [LRCLIB](https://lrclib.net/)

Otras fuentes, como Musixmatch, NetEase y Megalobiz, no están integradas todavía. Deezer y Lyricsify no forman parte de la versión actual.

## 📱 Compatibilidad

- **Android mínimo:** Android 6.0 (API 23).
- **API objetivo actual:** Android 14 (API 34).
- **Dispositivo probado:** Samsung Galaxy S24 Ultra con Android 16.

> La API objetivo actual deberá actualizarse antes de publicar CantaSync en Google Play.

## 🛠️ Tecnologías

- **Lenguaje:** Java
- **Plataforma:** Android nativo con Android SDK
- **Interfaz:** componentes nativos de Android
- **Compilación:** Gradle y Android Gradle Plugin

## 🔐 Permisos

- **Internet:** necesario para buscar letras en LRCLIB.
- **Acceso a notificaciones/sesiones multimedia:** opcional; permite detectar la canción activa y seguir su reproducción.

Android solicita autorización para el acceso al reproductor. Puedes buscar letras manualmente sin concederla.

## 🚧 Próximos pasos

- [ ] Integrar y evaluar fuentes adicionales de letras sincronizadas.
- [ ] Probar en más dispositivos y versiones de Android.
- [ ] Actualizar la API objetivo y preparar una versión para distribución.
- [ ] Publicar una primera versión estable.

## 📄 Licencia

El proyecto todavía no tiene una licencia de código abierto asignada. Hasta que se añada una, todos los derechos están reservados y no se concede permiso para reutilizar, modificar o distribuir el código.

## 🙌 Atribuciones

Las letras se obtienen de LRCLIB. CantaSync no incluye un catálogo propio de canciones.
