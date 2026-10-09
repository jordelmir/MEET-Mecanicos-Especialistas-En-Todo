# Elysium Safety
## Plataforma tecnológica para seguridad ciudadana, evidencia y análisis territorial

**Elysium Safety** busca fortalecer la capacidad de ciudadanos e instituciones para documentar, organizar, preservar, georreferenciar y analizar información relacionada con incidentes de seguridad.

No pretende sustituir a la Fuerza Pública, al Organismo de Investigación Judicial, al Ministerio Público ni al Poder Judicial. Su propósito es proporcionar una capa tecnológica de información y trazabilidad que facilite el trabajo de las instituciones competentes, según sus atribuciones y permisos.

## ¿Qué permite abordar?

### 1. Reportes estructurados de incidentes
La experiencia de Safety contempla un flujo para registrar categoría, relato, relación con la información, fecha de ocurrencia, ubicación cuando se conoce y material de respaldo. Entre las categorías del producto se incluyen situaciones de violencia, asaltos, homicidios, desapariciones, amenazas, actividad relacionada con drogas y otros incidentes.

La existencia de un formulario no significa que toda categoría tenga cobertura estadística completa ni que cada reporte haya sido verificado por una autoridad.

### 2. Evidencia y documentos vinculados
El código de Safety incluye almacenamiento local cifrado para adjuntos, validación de tipos/tamaño, hash SHA-256 de los bytes leídos, estados de carga y una ruta de verificación remota del contenido. La capacidad debe demostrarse en el APK y contra el almacenamiento del entorno de prueba antes de afirmarla como flujo operativo completo.

Fotografías, audios, documentos, enlaces de vídeo, ubicación y cronología deben permanecer vinculados a su reporte con IDs estables y una procedencia rastreable. Originales y derivados deben mantenerse separados y sujetos a la política de retención correspondiente.

### 3. Mapa e información territorial
La aplicación contiene un mapa de Safety con filtros por categoría y rango temporal y rutas separadas para puntos públicos y reportes privados. Una publicación geográfica requiere autorización, precisión adecuada y controles del servidor.

Un marcador solo significa que existe un registro accesible en esa vista. No confirma por sí mismo un delito. Una capa vacía tampoco prueba que no hayan ocurrido incidentes: puede reflejar cobertura incompleta, filtros o ausencia de datos publicados.

### 4. Trazabilidad y estados de conocimiento
El código distingue estados de conocimiento y tiene reglas para impedir promociones automáticas de una hipótesis. La interfaz debe separar:
- “Una persona reportó este acontecimiento”.
- “Existe un documento que respalda una afirmación concreta”.
- “Existen fuentes independientes que corroboran un aspecto”.
- “Una autoridad competente confirmó determinado hecho”, solo cuando haya un registro válido que lo demuestre.

Los estados de sincronización, verificación de bytes, disponibilidad de una integración y permisos tampoco deben confundirse con el estado epistemológico.

### 5. Cadena de evidencia e investigación
El repositorio contiene componentes de custodia con hashes, cadenas, checkpoints, árboles de Merkle, firma/verificación criptográfica y relaciones científicas entre entidades, afirmaciones, eventos, hipótesis y evidencia. Su ejecución, configuración y conexión completa deben verificarse para el SHA del APK que se presente.

Un hash acredita igualdad de bytes comparados; no demuestra que el documento sea verdadero, que la fuente sea independiente o que el contenido sea admisible judicialmente. Tampoco se afirmará que un QR está operativo hasta verificar la ruta real del registro, su autorización y la protección de datos.

### 6. Inteligencia territorial y cronología
El conjunto de registros, filtros y líneas de tiempo puede ayudar a organizar evolución temporal y distribución geográfica. Los patrones deben tener una cobertura suficiente, período explícito, procedencia y límites. No se deben fabricar patrones, cifras ni resultados de eficacia.

### 7. Colaboración ciudadano–institución
La plataforma pretende crear un puente tecnológico entre ciudadano, evidencia, información estructurada e institución competente. La interoperabilidad real requiere autorización, base jurídica, acuerdos, credenciales, seguridad y pruebas de extremo a extremo. El código de un gateway, sin configuración y validación con una contraparte, no demuestra una conexión operativa.

## Estado honesto de capacidades

- **Código presente:** formularios de reporte, modelos locales, adjuntos cifrados, hash y verificador, mapa, cronología, expedientes, observatorio y núcleo científico.
- **Parcialmente integrado / por verificar:** persistencia y sincronización completa en entorno desplegado; relación completa entre evidencia y afirmaciones; exposición geográfica; activación de gates; estados remotos y recibos.
- **Pendiente de piloto autorizado:** intercambio de datos con instituciones, protocolos de recepción, auditoría operativa y aceptación por las contrapartes.
- **Extensión separada:** inteligencia financiera de interés público basada en fuentes documentales legalmente accesibles, con reglas explicables y revisión humana.

La revisión de código de un SHA no sustituye la compilación, los tests del mismo commit, la prueba de staging ni la instalación del APK en el teléfono. Los materiales finales deben identificar el SHA probado y marcar como NOT_EXECUTED todo aquello que no se haya ejecutado.

## Potencial para Costa Rica

Una implementación institucional adecuada podría complementar los mecanismos existentes mediante participación ciudadana con salvaguardas, evidencia digital estructurada, análisis territorial, trazabilidad, cronologías reproducibles, preservación, correcciones auditables e interoperabilidad autorizada.

No se afirma que exista un despliegue nacional ni que la plataforma haya demostrado reducir delitos. El valor propuesto es que la información potencialmente relevante no se pierda y pueda revisarse con contexto, procedencia y limitaciones.

**Elysium Safety propone tecnología para que la información no se pierda.**

## Frase para abrir la reunión

> “Elysium Safety no pretende reemplazar a las autoridades ni decidir quién es culpable. Pretende resolver un problema anterior: cómo capturar, preservar, estructurar y analizar información de seguridad para que la evidencia no se pierda y pueda llegar a las instituciones competentes con trazabilidad.”

## Alcance de la demostración

La demostración para diputados se limita a Elysium Safety: reporte, evidencia, mapa, expedientes, cronología, observatorio e investigación. Esta separación es de presentación y navegación; no elimina del repositorio los módulos de mecánica, diagnóstico, movilidad, marketplace u otras capacidades de Elysium Vanguard AI OS.

La reunión debe mostrar capacidades verificadas y explicar qué necesita un piloto: fuente de datos autorizada, validación de privacidad, controles de acceso, recepción institucional, métricas y resultados reproducibles.
