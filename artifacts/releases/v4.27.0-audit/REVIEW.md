# Verificación del artefacto publicado y código actual

2026-09-26. Se descargó el único APK del release v4.27.0 y se calculó SHA-256: coincide con el digest de GitHub y las notas del release. El manifiesto contiene application-debuggable. No es evidencia de un release optimizado con R8.

Tras git fetch origin main, HEAD y origin/main coinciden en e4c379ddb00b275e390908232b8a70f83b0a0f81. Los avances posteriores están en el árbol local, preservados: no se deben atribuir al APK publicado. El release apunta a 70c20438 y main incluye un commit posterior.

El código de release activa minify y shrinkResources, excluye CAR2DB_API_KEY y MINIMAX_API_KEY_DEBUG y requiere una firma de producción. Se añadieron reglas R8 para quitar Logcat y printStackTrace. Falta compilar e inspeccionar el artefacto release resultante; esto no demuestra ausencia de secretos en el APK debug de ayer ni sustituye un análisis de DEX, recursos y bibliotecas.

Análisis limitado de DEX/assets/raw del APK publicado: no detectó marcadores sb_secret_. Dos PEM privados se atribuyeron mediante desensamblado a com.google.api.client.testing.json.webtoken.TestCertificates: certificados de prueba de dependencia, no credenciales identificadas del proyecto. Verificar que R8 elimine esta clase del release. Un JWT sin rol no permite declarar limpia toda la cadena. Nunca se imprimieron claves ni valores de tokens.

El archivo de compilación rechaza claves Supabase distintas de sb_publishable_ o JWT de rol anon, sin imprimirlas. Es una barrera contra credenciales privilegiadas, no una validación criptográfica del token ni una autorización de usuario.

download.html es un borrador local con el único texto solicitado y enlace previsto a app-release.apk. Ese asset todavía no está publicado: no desplegar la página hasta publicar y comprobar su destino y checksum. No se cambió la web/app. Google Play prueba interna usa AAB.

El escaneo con aapt2 y comparación exacta de valores privados locales confirmó que el APK debug publicado contiene MINIMAX_API_KEY_DEBUG. Nunca se imprimió su valor. Revocación/rotación del proveedor requerida por el dueño; excluirla de nuevos APK no invalida la clave ya expuesta. La página no debe promover el APK debug. Resultado reproducible: published-apk-scan.json. No se retiró ni modificó el release público sin autorización.

Validación de los avances locales anteriores a las reglas R8: assembleDebug y assembleDebugAndroidTest PASS; 17 pruebas unitarias seleccionadas PASS; 3 pruebas instrumentadas de cifrado y proyección de comunicaciones PASS en Honor VER-N49. Instalación y am start -W correctos. APK local SHA-256: 8eab3ade78ea6e189dbe163790618cb8967caff538f6512b3eb936de226300ae. Xiaomi sigue offline; no afirmar validación de este APK allí.
