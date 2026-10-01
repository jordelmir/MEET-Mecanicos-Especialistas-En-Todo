# Elysium Vanguard — identidad de la APK

La identidad utiliza las dos imágenes originales proporcionadas por el propietario.
`ev_logo_original.jpg` conserva el archivo del logo íntegro; `elysium_vanguard_banner.jpg`
conserva el arte horizontal íntegro. Inicio y Garage usan el arte original como fondo de pantalla completo, con
`ContentScale.Crop` para cubrir cada formato y una veladura para mantener la lectura.
Los controles se desplazan sobre el fondo. El banner de Centro de Viajes usa todo
el ancho disponible con `ContentScale.Fit`. El logo también aparece en PRO. El launcher utiliza el arte del automóvil
completo, ajustado al formato cuadrado en cinco densidades, en ambas variantes.

## Autoridad visual

El acceso de paleta en la barra superior abre el editor de la ruta actual. Cada canal
se resuelve independientemente: pantalla → dominio → global → fábrica.
Los cuatro canales de fábrica son `#39FF66`, `#00D9FF`, `#1677FF`, `#8255FF`.
`ElysiumTheme.colors` es la API semántica; `MeetColors` mantiene compatibilidad con
los renderizadores existentes. Material y Forge observan los colores reactivos.

El editor conserva el catálogo avanzado de colores, permite heredar un canal,
restablecer el alcance y trasladar una paleta al dominio o a global. La vista previa
es transitoria: Cancelar, cerrar o volver atrás la descartan. Guardar confirma el
borrador después de la escritura de DataStore; un fallo permanece visible.

DataStore `elysium_visual_theme` migra las preferencias históricas
`meet_system_theme_prefs`. La migración conserva los cuatro canales del usuario.
Las excepciones de pantalla y dominio se conservan al editar un antecesor.

## Semántica y rendimiento

Error, advertencia, éxito, gravedad, procedencia, categorías de mapas/gráficas,
materiales mecánicos y estilos explícitamente elegidos de instrumentos mantienen
su significado. No se recolorean las imágenes originales ni personajes.
Las transiciones de conexión, viajes, pagos, Safety y reportes siguen sus autoridades
existentes; el tema no crea estados funcionales.

El reloj de iconos y el fondo compartido se detienen fuera del primer plano, en
ahorro de energía o cuando el sistema o el usuario deshabilitan movimiento. Los
observadores se liberan al abandonar la composición. Los bordes metálicos comunes
no necesitan animación para permanecer visibles.

## Verificación

`ElysiumThemeConfigTest` cubre precedencia, herencia parcial, ARGB inválido, resets,
aislamiento entre alcances y clasificación de destinos Android reales.
La compilación conjunta y estas pruebas son necesarias antes de entregar una APK.
La inspección visual por ruta y los benchmarks de dispositivo son verificaciones
separadas; no se deducen de una compilación correcta.
