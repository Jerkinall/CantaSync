# Changelog

## v1.5.5-debug — 2026-10-18

### Añadido

- Comprobación manual del servicio LRCLIB, con indicador verde cuando responde y rojo cuando no está accesible.

### Corregido y mejorado

- Los errores de búsqueda ahora distinguen tiempos de espera, problemas para resolver el dominio, límites de solicitudes y errores del servidor; ya no atribuyen cualquier fallo a una falta de internet.
- Si el actualizador vuelve a validar una APK que ya está instalada, informa que la versión ya está actualizada en vez de mostrar un error.
- Actualización de versión a `1.5.5-debug`.

## v1.5.4-debug — 2026-10-09

### Añadido

- Nuevo icono de CantaSync con recursos adaptativos para Android 8.0+ y variantes heredadas para versiones anteriores.
- Actualización de versión a `1.5.4-debug`.

## v1.5.3-debug — 2026-10-09

### Añadido

- Comprobación manual de actualizaciones desde el menú de tres puntos; descarga el APK del release debug de GitHub, verifica paquete y firma, y abre el instalador de Android.
- Portadas en resultados mediante MusicBrainz/Cover Art Archive y portada del reproductor cuando coincide.
- Apariencias Claro, Oscuro y AMOLED.
- Acciones para copiar letras con tiempos, guardar .lrc y aplicar una letra creando un archivo junto al audio.

### Corregido y mejorado

- Guardado .lrc sin el sufijo accidental .txt.
- Confirmación de la ruta al guardar y al aplicar letras.
- Campos Artista y Canción reordenados, con mayúsculas iniciales al escribir.
- Diálogo de resultados más redondeado, contador visible, cancelar centrado y carátulas en cada resultado.
- Tarjeta de letra con título arriba a la izquierda, líneas alineadas a la izquierda y acciones compactas con iconos y etiquetas.
- Corrección de caracteres acentuados y resaltado de la línea sincronizada.

> Esta es una versión de depuración, no un lanzamiento estable. Android solicita confirmar la instalación de cada actualización.
