# Fase 10-15 — Parte III (Proyecto Final)

Cotejo punto por punto de `Proyecto_General_vf.md` Parte III contra la
implementación, con las decisiones de diseño e interpretaciones tomadas
(mismo formato que los checklists de Fase 7/8/9). A diferencia de la
validación de Parte II (`test-partII/test-partII.md`), **esta fase no se
validó en vivo end-to-end**: Kafka y Redis se probaron con mocks/tests
unitarios, no con contenedores reales (decisión explícita, ver más abajo).

## Bases técnicas obligatorias

| Requisito | Estado | Evidencia |
|---|---|---|
| Programación funcional y reactiva | ✅ | Toda la capa de servicio nueva sigue en `Mono`/`Flux` (Reactor), igual que Parte I/II; sin `for`/`while` imperativos |
| Manejo de colecciones con Streams | ✅ | `resolveStrategy`, filtros de eventos Kafka, etc. usan `Stream`/lambdas, no bucles |
| Pruebas unitarias con mocks para los métodos públicos nuevos | ✅ | Un test class por servicio/listener nuevo (`DebitCardServiceImplTest`, `YankiWalletServiceImplTest`, `YankiAccountRequestListenerTest`, `YankiAccountResponseListenerTest`, `AuthServiceImplTest`, `JwtAuthenticationFilterTest`, más los tests agregados a `CreditServiceImplTest`/`AccountServiceImplTest`/`CustomerServiceImplTest` existentes) |
| Reporte de coverage | ✅ | Jacoco ya configurado en los 7 `pom.xml` (heredado de Fase 7); `target/site/jacoco/index.html` tras `mvn test` en cada servicio, incluyendo `yanki-service` |
| Arquitectura orientada a eventos con Kafka | ✅ | Tópicos `yanki.account.requests`/`yanki.account.responses` entre `yanki-service` y `account-service` (Fase 12) |
| Microservicios nuevos sin REST entre sí | ✅ | `yanki-service` (el único microservicio nuevo) no tiene ningún `WebClient`/`RestTemplate` hacia otro microservicio; toda su integración con `account-service` es Kafka. Confirmado con `grep -r WebClient yanki-service/src/main` → sin resultados |
| Controladores reactivos con RxJava + Spring | ⚠️ Parcial, ver nota | Los endpoints genuinamente nuevos (`DebitCardController`, `YankiWalletController`, `AuthController`) devuelven `Single`/`Flowable`/`Completable` vía `RxJava3Adapter`. Los controladores de Parte I/II (`AccountController`, `CreditController`, `CustomerController`) **no** se reescribieron a RxJava — ver nota de alcance abajo |
| Autenticación y autorización con JWT | ✅ | Login en `customer-service`, validación en `api-gateway` (Fase 13) |
| Cache Redis para datos catalogados/maestros | ✅ | `customer-service` cachea `Customer` por id (Fase 14) |
| Diagrama draw.io actualizado | ✅ | `docs/architecture/architecture.drawio` actualizado con `yanki-service`, Kafka y Redis |

### Nota de alcance — RxJava

La consigna dice "los controladores que implementen las nuevas
funcionalidades deberán ser reactivos usando RxJava". Se interpretó
literalmente: **solo** los controladores de endpoints nuevos usan RxJava
(`DebitCardController`, `YankiWalletController`, `AuthController`), no los
14 endpoints preexistentes de `AccountController`/`CreditController`/`CustomerController`
de Parte I/II. Reescribirlos habría sido una reescritura de bajo valor y
alto riesgo de regresión sobre código ya probado en Parte II, y la
consigna dice explícitamente "las nuevas funcionalidades". La capa de
servicio/repositorio sigue en Reactor en todos los casos (Fase 12
necesita `Mono`/`Flux` para las llamadas a Mongo/Kafka de todas formas);
la conversión a RxJava ocurre solo en el borde del controller vía
`reactor.adapter.rxjava.RxJava3Adapter`.

### Nota de alcance — validación de Kafka y Redis

A diferencia de Parte II (`docs/verification/test-partII/`, validada con
el stack completo corriendo en vivo), esta fase se probó así:

- **Kafka**: se mockeó `KafkaTemplate` para el lado productor y se invocó
  el método `@KafkaListener` directamente (no es un endpoint HTTP) para el
  lado consumidor, en ambos servicios. No se levantó un broker Kafka real
  ni se usó `@EmbeddedKafka`.
- **Redis**: se mockearon `ReactiveRedisTemplate`/`ReactiveValueOperations`
  en `CustomerServiceImplTest` (hit, miss, invalidación).

Fue una decisión explícita del usuario para esta sesión (evitar el costo
de levantar/depurar infraestructura adicional, ya evaluado en Parte II).
El código y `docker-compose.yml` quedan listos para `docker compose up`,
pero **no se confirmó en vivo** que el broker/Redis reales funcionen con
esta configuración exacta.

## Funcionalidades del sistema

### "Un cliente no podrá adquirir un producto si posee alguna deuda vencida en algún producto de crédito"

| Requisito | Estado | Evidencia |
|---|---|---|
| Modelo de "deuda vencida" | ✅ | `CreditProduct.nextPaymentDueDate`; vencido = `balance > 0 && nextPaymentDueDate` ya pasó |
| Bloqueo al otorgar un crédito nuevo | ✅ | `CreditServiceImpl.validateAndPrepare` → `hasOverdueDebt` (chequeo local, sin REST) |
| Bloqueo al abrir una cuenta nueva | ✅ | `AccountServiceImpl.validateAndPrepare` → `CreditClient.hasOverdueDebt` (mismo patrón/circuit breaker que `hasActiveCreditCard` de Fase 8) |

**Nota de interpretación:** la consigna no define un cronograma de cuotas
explícito para créditos personales/empresariales ni para tarjetas. Se
adoptó el modelo más simple consistente con el resto del proyecto
(similar a la interpretación pragmática de "un crédito por persona" ya
documentada en `business-rules-checklist.md`): cada crédito tiene una
única `nextPaymentDueDate` que se reprograma un mes hacia adelante en
cada pago parcial, en vez de un plan de cuotas completo con montos
individuales.

### "Un cliente puede hacer el pago de cualquier producto de crédito de terceros"

| Requisito | Estado | Evidencia |
|---|---|---|
| Pago de un crédito ajeno | ✅ | `POST /api/credits/{id}/payments` con `payerCustomerId` opcional; sin restricción de que el pagador sea el titular |
| Validación de que el pagador exista | ✅ | `CreditServiceImpl.validatePayerExists` vía `CustomerClient` |
| Trazabilidad | ✅ | `CreditMovement.payerCustomerId` (el titular si se omite) |

### "Los clientes ahora pueden tener tarjetas de débito asociadas a sus cuentas bancarias y hacer pagos con ellas"

| Requisito | Estado | Evidencia |
|---|---|---|
| Tarjeta asociada a una cuenta bancaria | ✅ | `DebitCard{accountId, customerId}`, `POST /api/debit-cards`; valida que la cuenta pertenezca al cliente y no tenga ya otra tarjeta activa |
| Pagos con la tarjeta | ✅ | `POST /api/debit-cards/{id}/payments` → `AccountService.payWithDebitCard` (mismas reglas/comisión que un retiro, tipo `DEBIT_CARD_PAYMENT`) |
| Historial de movimientos de la tarjeta | ✅ | `GET /api/debit-cards/{id}/movements` |

**Decisión de diseño (confirmada con el usuario):** se extendió
`account-service` en vez de crear un microservicio nuevo, porque la
tarjeta es 1:1 con una cuenta ya existente en ese servicio y reutiliza
`AccountMovement`/`AccountRuleStrategy` directamente. Esto significa que
no aplica la restricción "sin REST entre microservicios nuevos" (no es un
microservicio nuevo).

### Monedero móvil Yanki

| Requisito | Estado | Evidencia |
|---|---|---|
| Registro sin ser cliente del banco (DNI/CEX/Pasaporte + celular + IMEI + correo) | ✅ | `YankiWallet`, `POST /api/yanki/wallets`; no referencia a `customer-service` en ningún punto |
| Enviar/recibir pagos por número de celular | ✅ | `POST /api/yanki/transfers` `{senderPhone, receiverPhone, amount}`, resuelve ambos monederos por `phoneNumber` |
| Asociar el monedero a una tarjeta de débito del banco | ✅ | `POST /api/yanki/wallets/{id}/link-debit-card` (asíncrono vía Kafka, `LINK_CARD`) |
| Saldo cargado/acreditado solo a la cuenta principal de esa tarjeta | ✅ | `linkedAccountId` se resuelve una sola vez al vincular (desde `DebitCard.accountId`) y es el único destino de `load`/`withdraw`; no se puede indicar otra cuenta |

**Flujo asíncrono:** los 3 endpoints que dependen de `account-service`
(`link-debit-card`, `load`, `withdraw`) devuelven `202 Accepted` con una
`YankiOperation` en `PENDING`; el resultado real llega por Kafka y se
consulta con `GET /api/yanki/operations/{correlationId}`. Esto es una
decisión de diseño explícita (no está en la consigna) necesaria para
respetar "sin REST entre microservicios nuevos" de forma honesta: sin
esto, la alternativa hubiera sido simular sincronía con un
request-reply-over-Kafka bloqueante, más complejo y frágil para el
alcance de este proyecto.

## Artefactos y entregables

| Requisito | Estado | Evidencia |
|---|---|---|
| Repositorio con los proyectos Postman | ⚠️ Pendiente | La colección Postman existente (`docs/postman/`) cubre Parte I/II; no se actualizó con los endpoints de Parte III (débito, Yanki, auth) por alcance/tiempo de esta sesión — el archivo es grande y editarlo a ciegas es más riesgoso que dejarlo pendiente y documentado |
| Cada microservicio en su propio repositorio | ⚠️ Igual que en Parte I/II | Ver nota ya existente en `business-rules-checklist.md`: los microservicios viven en una carpeta raíz común mientras dure el desarrollo conjunto, pero no comparten dependencias Maven y pueden promoverse a repos independientes sin cambios de código. `yanki-service` sigue exactamente el mismo patrón que los demás |

## Verificación realizada

- `mvn test` (JDK 21, ver nota de `CLAUDE.md`) en `credit-service`,
  `account-service`, `customer-service` y `yanki-service`: compilan y
  pasan, sin regresiones sobre los 9 tests preexistentes y no
  relacionados ya documentados en `CLAUDE.md`.
- No se ejecutó `mvn verify` (Checkstyle) de forma explícita en esta
  sesión sobre los archivos nuevos, más allá de que el plugin ya corre en
  modo no bloqueante como parte de `verify` en los 7 `pom.xml`.
- No se levantó el stack completo en vivo (ver nota de alcance de
  Kafka/Redis arriba).
