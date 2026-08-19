package com.elysium369.meet.core.transport

import java.io.IOException

sealed class TransportException(message: String, cause: Throwable? = null) : IOException(message, cause)

class TransportConnectTimeout(message: String = "Tiempo de espera de conexión agotado") : TransportException(message)

class TransportRemoteClosed(message: String = "El adaptador remoto cerró el enlace de comunicación") : TransportException(message)

class TransportWriteFailure(message: String = "Error al enviar bytes al canal físico", cause: Throwable? = null) : TransportException(message, cause)

class TransportReadFailure(message: String = "Fallo de I/O al leer del socket Bluetooth", cause: Throwable? = null) : TransportException(message, cause)

class TransportPermissionDenied(message: String = "Permiso de Bluetooth no otorgado") : TransportException(message)

class TransportPairingRequired(message: String = "El adaptador requiere emparejamiento manual previo") : TransportException(message)

class AdapterNoPrompt(message: String = "El adaptador no emitió el prompt '>' esperado") : TransportException(message)

class EcuNoResponse(message: String = "La ECU del vehículo no respondió en el protocolo activo") : TransportException(message)
