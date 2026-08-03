# Colección Postman — Sistema Bancario (Microservicios)

Colección única con los endpoints de los 4 microservicios de negocio
(`customer-service`, `account-service`, `credit-service`, `yanki-service`),
cubriendo las 3 partes del proyecto.

## Archivos

- **banking-microservices.postman_collection.json** — colección organizada
  en carpetas, en el orden en que se deben ejecutar:
  1. Customer Service
  2. Account Service
  3. Credit Service
  4. Fase 8 - Perfiles VIP / PYME y comisiones
  5. *(reservado — reportes/transferencias de Fase 9, ver nota abajo)*
  6. Auth (JWT, Fase 13)
  7. Debit Cards (Fase 11, Parte III)
  8. Yanki Wallet (Fase 12, Parte III)
  9. Cleanup (ejecutar al final)
- **banking-microservices.postman_environment.json** — environment
  "Banking Microservices - Local" con las URLs base
  (`customerServiceUrl`, `accountServiceUrl`, `creditServiceUrl`,
  `yankiServiceUrl`, `gatewayUrl`) y las variables que se van completando
  automáticamente durante la ejecución (`personalCustomerId`,
  `savingsAccountId`, `debitCardId`, `walletAId`, `authToken`, etc.).

> Nota: las carpetas 1-4 no incluyen todavía las requests de transferencias
> ni de los reportes por rango de fechas / últimos 10 movimientos (Fase 9);
> se pueden ejercitar manualmente contra `POST /api/accounts/{id}/transfers`,
> `GET /api/accounts/{id}/movements/report` y
> `GET /api/credits/{id}/movements/last-10` con las mismas variables de
> entorno ya definidas (`savingsAccountId`, `creditCardId`, etc.), y quedan
> documentadas con ejemplos reales de request/response en
> `docs/verification/test-partII/test-partII.md` (solo en local, no
> versionado en este repositorio).

## Cómo usar

1. Importar ambos archivos en Postman (`File > Import`).
2. Seleccionar el environment **Banking Microservices - Local**.
3. Levantar los 7 microservicios (`eureka-server` y `config-server`
   primero, luego `customer-service`, `account-service`, `credit-service`,
   `yanki-service`, y por último `api-gateway`), ya sea de forma local
   (`mvn spring-boot:run` en cada uno) o con `docker compose up --build`
   desde la raíz del proyecto.
4. Para las carpetas 6-8 (Parte III) además se necesita:
   - **Redis** accesible en `localhost:6379` — sin él, `customer-service`
     rechaza incluso una simple consulta de cliente por id (`GET
     /api/customers/{id}` usa cache Redis en el camino). Alcanza con
     `docker run -d -p 6379:6379 redis:7-alpine` si no se usa Docker
     Compose completo.
   - **Kafka** accesible en `localhost:9092` para que las operaciones
     asíncronas del monedero Yanki (vincular tarjeta, cargar, retirar)
     respondan `202 Accepted` y pasen de `PENDING` a `COMPLETED`. Sin Kafka
     corriendo, esas 3 requests responden `500 Internal Server Error` a los
     ~3 segundos (el productor intenta resolver los metadatos del tópico y
     desiste) en vez de colgarse esperando: verificado en vivo con
     `time curl` antes y después de acotar `max.block.ms` en
     `KafkaConfig` (por defecto son 60s).
5. Ejecutar la colección completa con el **Collection Runner**, carpeta por
   carpeta y en orden (1 → 2 → 3 → 4 → 6 → 7 → 8 → 9), ya que las requests
   posteriores dependen de ids guardados automáticamente por los scripts de
   test de las requests anteriores mediante `pm.environment.set(...)`.

> **Nota:** también se agregó la variable **`gatewayUrl`**
> (`http://localhost:8080`): si se prefiere probar todo a través del API
> Gateway (con su circuit breaker Resilience4j de por medio), basta
> reemplazar `customerServiceUrl`/`accountServiceUrl`/`creditServiceUrl`/
> `yankiServiceUrl` por `{{gatewayUrl}}` en el environment. La carpeta 6
> (Auth) ya usa `{{gatewayUrl}}` explícitamente para demostrar el flujo
> completo: login directo contra `customer-service`, y luego una ruta
> protegida a través del gateway con el token obtenido.

## Qué se verifica

Además del CRUD estándar de cada recurso, la colección incluye casos
explícitos marcados con **[Regla de negocio]** que verifican las reglas
propias de la consigna:

- Un cliente personal no puede abrir una segunda cuenta de ahorro.
- Un cliente personal no puede tener más de un crédito personal activo.
- Una empresa sí puede tener múltiples créditos empresariales.
- Un consumo de tarjeta que excede el límite disponible es rechazado.
- Un producto de crédito que no es tarjeta no admite consumos.

### Fase 8 (carpeta 4)

- Un cliente **VIP** solo puede ser personal (`[Regla de negocio] Rechaza
  perfil VIP para cliente empresarial`, en la carpeta 1).
- Una cuenta de ahorro **VIP** exige `minimumDailyAverageBalance` y que el
  cliente ya tenga una tarjeta de crédito activa (se otorga primero en la
  carpeta 3, antes de abrir la cuenta en la carpeta 4).
- Una cuenta corriente **PYME** fuerza `maintenanceFee` a `0` sin importar
  el valor enviado, y también exige tarjeta de crédito previa.
- Abrir una cuenta con un `balance` inicial menor a `minimumOpeningAmount`
  se rechaza con 400.
- Al superar `freeMonthlyTransactionLimit` en un mismo mes, la siguiente
  transacción genera además un movimiento `FEE` por `transactionFeeAmount`
  (verificado consultando `/movements` tras el segundo depósito).

### Auth / JWT (carpeta 6)

- Registro de credenciales y login devuelven un JWT usable como
  `Authorization: Bearer <token>`.
- Login con contraseña incorrecta se rechaza con `401`.
- Una ruta protegida a través del API Gateway responde `200` con el token
  y `401` sin él — confirma que el `JwtAuthenticationFilter` del gateway
  efectivamente intercepta las rutas antes de reenviarlas al microservicio.

### Debit Cards (carpeta 7)

- Una cuenta admite como máximo una tarjeta de débito activa
  (`[Regla de negocio]`, `409 Conflict` en el segundo intento).
- Un pago con tarjeta se registra como movimiento `DEBIT_CARD_PAYMENT`
  (no `WITHDRAWAL`), verificado en `GET .../movements`.

### Yanki Wallet (carpeta 8)

- El registro de un monedero no requiere ningún dato de `customer-service`
  (documento, celular, IMEI y correo alcanzan).
- Un segundo monedero con el mismo número de celular se rechaza con `409`.
- Vincular tarjeta, cargar y retirar saldo son asíncronos vía Kafka:
  responden `202 Accepted` de inmediato con la operación en `PENDING`, y el
  resultado se confirma consultando
  `GET /api/yanki/operations/{correlationId}`.
- La transferencia entre monederos (por número de celular) es síncrona y
  no pasa por Kafka.

Cada request incluye un test de Postman (`pm.test`) que valida el código
de estado HTTP esperado (y, en varios casos, también el cuerpo de la
respuesta), de forma que la colección entera puede ejecutarse con el
Collection Runner o con `newman` y reportar éxito/fallo por request.
