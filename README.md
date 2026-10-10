# CantaSync

CantaSync busca letras sincronizadas por línea, muestra portadas cuando están disponibles y permite guardar o aplicar las letras como archivos .lrc.

> **Estado:** v1.5.4-debug, versión de prueba. Incluye el nuevo icono de CantaSync; sigue sin ser un lanzamiento estable y necesita más pruebas en dispositivos.

## Funciones

- Buscar letras sincronizadas en [LRCLIB](https://lrclib.net/) y escoger entre coincidencias.
- Ver portadas de [MusicBrainz](https://musicbrainz.org/) y [Cover Art Archive](https://coverartarchive.org/), o la portada del reproductor activo cuando coincide.
- Detectar título, artista y progreso de un reproductor compatible mediante el acceso opcional a notificaciones de Android.
- Copiar letras con marcas de tiempo, guardar un archivo .lrc en la carpeta elegida o aplicar la letra creando un .lrc junto al MP3, FLAC o WAV correspondiente dentro de Music y sus subcarpetas.
- Elegir apariencia Clara, Oscura o AMOLED.
- Usar el icono adaptativo de CantaSync, con variantes para lanzadores Android modernos y antiguos.
- Buscar actualizaciones desde el menú de tres puntos. CantaSync consulta los releases debug de GitHub, descarga el APK, verifica que corresponda a la app y que tenga la misma firma, y luego abre el instalador de Android.

La sincronización y el resaltado son por línea, no palabra por palabra. Aplicar una letra crea un archivo .lrc separado y no modifica los metadatos del audio.

## Actualizaciones

Abre **⋮ → Buscar actualizaciones**. Si hay una versión nueva, CantaSync la descarga y verifica su paquete y firma antes de iniciar el instalador. Android siempre pide confirmar la instalación y puede solicitar permiso para instalar aplicaciones descargadas por CantaSync. Las actualizaciones requieren la misma firma que la versión instalada.

## Compatibilidad

- Android 6.0 (API 23) o posterior.
- API objetivo: Android 14 (API 34).
- Fuentes de letras: LRCLIB. MusicBrainz y Cover Art Archive se usan solo para portadas.

## Apariencia y tipografía

La paleta combina negro, blanco, morado y cian. AMOLED es el modo inicial. El título solicita Segoe UI Bold si está instalada; el resto usa pesos Medium y Light del sistema. La app no incluye una copia de Segoe UI.

## Permisos

- **Internet:** buscar letras, portadas y releases de GitHub.
- **Acceso al reproductor:** opcional; permite detectar la canción y sincronizar el resaltado.
- **Instalación de APK:** Android puede pedir permiso para abrir el instalador de una actualización descargada desde el release de GitHub.

## Licencia

El proyecto todavía no tiene una licencia de código abierto asignada. Todos los derechos están reservados.

Consulta [CHANGELOG.md](CHANGELOG.md) para los cambios de v1.5.4-debug.
