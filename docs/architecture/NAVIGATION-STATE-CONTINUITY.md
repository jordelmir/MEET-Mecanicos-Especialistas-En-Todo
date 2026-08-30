# Continuidad de navegación y estado

Versión de contrato: Android `4.22.1` (`versionCode 49`).

## Garantías

- Una recarga transitoria de la sesión cifrada no destruye el grafo ya
  autorizado, sus `ViewModel` ni el servidor LiveLink compartido.
- Un resultado explícito de sesión no autenticada sí descarta el grafo. La
  continuidad local nunca concede autoridad de nube ni de proveedor.
- El botón físico y todos los controles visibles de volver retiran exactamente
  una entrada del historial. Si un enlace profundo no tiene padre utilizable,
  el destino seguro es Inicio. Inicio no se duplica.
- Inicio, Scanner, DTC, Garage y PRO guardan y restauran su pila independiente.
- Los borradores de vehículo, viajes, mecánica, grúa, repuestos, registro de
  proveedores, red de reparación y reportes usan estado guardable. Sobreviven
  a recomposición, cambio de sección y recreación normal de Activity/proceso.
- Forge conserva su historial interno y, al llegar a la raíz del módulo, vuelve
  al punto real desde el que fue abierto en MEET.

## Trabajo activo frente a estado de interfaz

Android puede terminar un proceso para recuperar memoria; ninguna aplicación
puede prometer lo contrario. MEET separa por ello dos responsabilidades:

- los datos y borradores restaurables pertenecen al estado guardado o a los
  repositorios locales durables;
- una sesión OBD iniciada por la persona usa `ObdForegroundService`, notificación
  visible y `WakeLock` acotado. No se inventa una reconexión ni se reinicia una
  orden de diagnóstico sin intención explícita.

Eliminar la tarea, borrar datos o forzar detención es una acción explícita del
usuario/sistema y no se trata como una recreación normal. Datos ya confirmados
siguen dependiendo de Room/Supabase; contraseñas y secretos nunca se guardan
como borradores de pantalla.

## Verificación mínima de release

1. Pruebas puras de política de historial y retención de sesión.
2. Compilación Kotlin, pruebas unitarias, lint y paridad TS/Kotlin.
3. APK de producción firmada y hash verificado.
4. En HONOR: instalación, arranque, proceso/actividad en primer plano, recorrido
   profundo, retorno ordenado, fondo/primer plano y ausencia de crash/ANR.
