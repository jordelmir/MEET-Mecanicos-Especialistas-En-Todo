# Viajes V5 — Live Share

Estado: no desplegado.

Contrato: `ride_share_sessions` con token aleatorio de al menos 128 bits, solo hash en BD, scopes mínimos, expiración y revocación automática. El endpoint web público devuelve una proyección sanitizada y no permite distinguir un token inválido de un viaje inexistente. Compartir texto estático no satisface este contrato.
