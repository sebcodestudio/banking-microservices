# Sistema Bancario — Microservicios

Sistema de microservicios bancarios en Spring Boot (WebFlux + Project
Reactor) y MongoDB, con Config Server para configuración externalizada,
Eureka para registro y descubrimiento de servicios, y un API Gateway
(Spring Cloud Gateway) como punto de entrada único con circuit breaker
(Resilience4j). La consigna original del proyecto está en
[`nota.md`](./nota.md).

## Microservicios

| Microservicio | Puerto | Responsabilidad |
|---|---|---|
| [`eureka-server`](./eureka-server) | 8761 | Registro y descubrimiento de servicios (Netflix Eureka); panel en `http://localhost:8761` |
| [`api-gateway`](./api-gateway) | 8080 | Punto de entrada REST único; enruta a los 4 microservicios de negocio vía `lb://` (Eureka) con circuit breaker Resilience4j (timeout 2s), y valida el JWT en rutas protegidas (Fase 13) |
| [`config-server`](./config-server) | 8888 | Configuración externalizada de los demás microservicios (perfil `native`, con variante `docker` por servicio) |
| [`customer-service`](./customer-service) | 8081 | Clientes personales y empresariales; login/emisión de JWT y cache Redis de clientes (Fases 13-14) |
| [`account-service`](./account-service) | 8083 | Cuentas bancarias: ahorro, corriente y plazo fijo; depósitos, retiros, saldo y movimientos; tarjetas de débito (Fase 11); puente Kafka con `yanki-service` (Fase 12) |
| [`credit-service`](./credit-service) | 8082 | Productos de crédito: personal, empresarial y tarjeta de crédito; pagos, consumos, saldo y movimientos; deuda vencida y pago a terceros (Fase 10) |
| [`yanki-service`](./yanki-service) | 8084 | Monedero móvil Yanki (Fase 12) — no requiere ser cliente del banco; se comunica con `account-service` únicamente vía Kafka, nunca REST |

Cada microservicio tiene su propio `pom.xml` (sin `pom.xml` padre
agregador) y su propia base MongoDB (`customer_db`, `account_db`,
`credit_db`, `yanki_db`), respetando el patrón *database per service*.
Todos se registran como clientes de Eureka; `account-service` y
`credit-service` resuelven a `customer-service` (y `account-service` a
`credit-service`) vía `lb://` en vez de una URL fija.

## Perfiles VIP / PYME y comisiones (Fase 8)

- **Monto mínimo de apertura** — `BankAccount.minimumOpeningAmount`
  (puede ser cero), validado contra el `balance` inicial al abrir la
  cuenta.
- **Perfil VIP** (solo clientes personales) — cuenta de ahorro con
  `minimumDailyAverageBalance` obligatorio y que exige tener ya una
  tarjeta de crédito activa con el banco.
- **Perfil PYME** (solo clientes empresariales) — cuenta corriente con
  `maintenanceFee` forzado a `0`, con el mismo requisito de tarjeta de
  crédito previa.
- **Comisión por exceso de transacciones** — todas las cuentas tienen
  `freeMonthlyTransactionLimit`/`transactionFeeAmount`; superado el
  límite mensual, cada depósito/retiro adicional genera además un
  movimiento `FEE`.
- Nueva integración **`account-service` → `credit-service`**
  (`CreditClient`, vía Eureka + circuit breaker Resilience4j de 2s) y
  nuevo endpoint interno `GET /api/credits/customers/{customerId}/has-credit-card`.
- Detalle completo en `docs/verification/fase8-vip-pyme-checklist.md`.

## Transferencias y reportes (Fase 9)

- **Transferencias** — `POST /api/accounts/{id}/transfers`, con `{ destinationAccountId, amount }`
  en el body. Cubre tanto transferencias entre cuentas del mismo cliente
  como hacia cuentas de un tercero del mismo banco: ambos casos son el
  mismo mecanismo, ya que la titularidad no cambia la mecánica de mover
  fondos entre dos cuentas de `account-service`. Internamente se aplica
  un retiro (`TRANSFER_OUT`) en la cuenta origen y un depósito
  (`TRANSFER_IN`) en la destino, cada uno sujeto a las mismas reglas por
  tipo de cuenta y a la comisión por exceso de transacciones (Fase 8) que
  un retiro/depósito normal.
- **Reporte general por producto en un intervalo de fechas** —
  `GET /api/accounts/{id}/movements/report?startDate=&endDate=` y
  `GET /api/credits/{id}/movements/report?startDate=&endDate=`
  (`Instant` en formato ISO-8601). Se interpretó "reporte por producto"
  como el reporte de movimientos de un producto puntual (una cuenta o un
  crédito), consistente con el resto de endpoints de consulta ya
  existentes (`GET .../{id}/movements`).
- **Últimos 10 movimientos de tarjeta** —
  `GET /api/credits/{id}/movements/last-10`, solo para créditos de tipo
  `CREDIT_CARD` (rechaza con 400 cualquier otro tipo). El reporte
  equivalente para tarjeta de débito queda pendiente: ese producto recién
  se incorpora en la Parte III de la consigna (junto con los pagos con
  tarjeta de débito), por lo que no existe todavía en el modelo de datos.
- Detalle completo en `docs/verification/fase9-transfers-reports-checklist.md`.

## Parte III (Fases 10-14)

Deuda vencida, pago a terceros, tarjetas de débito, monedero móvil Yanki
(arquitectura orientada a eventos con Kafka), autenticación JWT y cache
Redis. Detalle completo, con las decisiones de diseño e interpretaciones
tomadas, en `docs/verification/fase10-15-parte3-checklist.md`.

- **Deuda vencida (Fase 10)** — `CreditProduct.nextPaymentDueDate` (se
  completa a un mes desde el otorgamiento si no se especifica, y se
  adelanta un mes en cada pago que no salda el crédito). Un cliente con
  algún producto de crédito con balance pendiente y cuota vencida no
  puede adquirir un crédito nuevo (`credit-service`) ni abrir una cuenta
  nueva (`account-service`, vía `CreditClient.hasOverdueDebt`,
  `GET /api/credits/customers/{customerId}/has-overdue-debt`).
- **Pago de crédito de terceros (Fase 10)** — `POST /api/credits/{id}/payments`
  admite `payerCustomerId` opcional; si se indica, se valida contra
  `customer-service` que exista, y queda registrado en el movimiento
  aunque sea distinto del titular del crédito.
- **Tarjetas de débito (Fase 11)** — nuevo recurso en `account-service`
  (`/api/debit-cards`), 1:1 con una cuenta bancaria existente; un pago con
  la tarjeta se registra como un retiro sobre esa cuenta
  (`MovementType.DEBIT_CARD_PAYMENT`), con las mismas reglas y comisión
  por exceso de transacciones que un retiro normal.
- **Monedero Yanki (Fase 12)** — nuevo microservicio `yanki-service`; el
  registro solo requiere documento de identidad, celular, IMEI y correo
  (no hace falta ser cliente del banco). Envía/recibe saldo por número de
  celular (`POST /api/yanki/transfers`) y puede vincularse a una tarjeta
  de débito para cargar/retirar saldo hacia esa cuenta
  (`POST /api/yanki/wallets/{id}/link-debit-card`, `.../load`, `.../withdraw`).
  Cumpliendo la consigna, **no hay ninguna llamada REST** entre
  `yanki-service` y `account-service`: toda la integración va por dos
  tópicos de Kafka (`yanki.account.requests`/`yanki.account.responses`).
  Los endpoints que dependen de esa integración responden `202 Accepted`
  de inmediato; el resultado se consulta con
  `GET /api/yanki/operations/{correlationId}`.
- **RxJava** — los endpoints nuevos de Parte III (`DebitCardController`,
  `YankiWalletController`, `AuthController`) devuelven `Single`/`Flowable`/`Completable`
  en vez de `Mono`/`Flux`, convertidos con `RxJava3Adapter` sobre la misma
  capa de servicio en Reactor; los controladores de Parte I/II no se
  tocaron.
- **Autenticación JWT (Fase 13)** — `POST /api/auth/login` en
  `customer-service` (usuario/contraseña, hash con BCrypt) emite el
  token; `POST /api/customers/{id}/credentials` registra las credenciales
  de un cliente existente. `api-gateway` valida la firma/expiración del
  JWT en toda ruta salvo el login y el alta de cliente.
- **Cache Redis (Fase 14)** — `customer-service` cachea `findById` (el
  dato más consultado entre servicios, vía `CustomerClient`) con
  `ReactiveRedisTemplate`, TTL de 5 minutos, invalidado en
  actualización/baja.
- Kafka y Redis se agregaron a `docker-compose.yml` pero **no se
  validaron en vivo** en esta sesión (a diferencia de Parte II): se
  probaron con mocks/tests unitarios, ver el checklist para el detalle.

## Infraestructura transversal (Fase 7)

- **Eureka** — descubrimiento de servicios; ver panel en `:8761`.
- **API Gateway** — único punto de entrada REST recomendado para Postman
  (`http://localhost:8080/api/...`), en vez de pegarle a cada puerto por
  separado.
- **Resilience4j** — circuit breaker + `TimeLimiter` con **timeout de 2
  segundos**, aplicado tanto en el gateway (por ruta) como en la llamada
  interna `account-service`/`credit-service` → `customer-service`
  (`CustomerClient`). Si se excede el timeout o el circuito está abierto,
  se responde con 503 en vez de colgar la petición.
- **Patrón Strategy** — las reglas propias de cada subtipo de cuenta
  (`AccountRuleStrategy`) y de cada subtipo de crédito
  (`CreditGrantStrategy`) están encapsuladas en estrategias
  intercambiables, en vez de una cadena de `instanceof` en el servicio.
- **Checkstyle** — `checkstyle.xml` compartido, plugin agregado en los 7
  `pom.xml` (fase `verify`, no bloqueante).
- **Jacoco** — reporte de cobertura de pruebas en los 7 `pom.xml`
  (`target/site/jacoco/index.html` tras `mvn test`).
- **Tests unitarios** — JUnit 5 + Mockito + `reactor-test` (`StepVerifier`)
  para los 4 servicios de negocio y sus estrategias.

## Documentación consolidada (`docs/`)

- **`docs/architecture/architecture.drawio`** — diagrama de arquitectura general.
- **`docs/sequence-diagrams/`** — un diagrama de secuencia por microservicio de negocio.
- **`docs/openapi/`** — contratos OpenAPI, uno por servicio (con README explicativo).
- **`docs/postman/`** — colección Postman con los endpoints de Parte I/II + environment (Parte III pendiente de agregar).
- **`docs/verification/`** — revisión cruzada de reglas de negocio contra la
  consigna original y checklist final de código (comentarios, lambdas/streams,
  ausencia de hardcodeo), más las validaciones **en vivo** (peticiones HTTP
  reales, no solo lectura de código) por parte del proyecto:
  `test-partI/test-partI.md` (clientes, cuentas, créditos) y
  `test-partII/test-partII.md` (VIP/PYME, comisiones, transferencias,
  reportes, infraestructura). Parte III solo se validó con tests
  unitarios/mocks, no en vivo (ver `fase10-15-parte3-checklist.md`).

## Cómo ejecutar

### Opción A — Local (Maven, sin Docker)

Requiere JDK 17 (ver nota de JDK 21 vs. 25 en `CLAUDE.md` si Lombok falla
al compilar), Maven, una instancia de MongoDB corriendo en
`localhost:27017`, y — solo para las funcionalidades de Parte III que la
usan (Fase 12) — Kafka en `localhost:9092`. Sin Kafka corriendo, todo lo
demás (Partes I y II, y Parte III salvo el monedero Yanki y las tarjetas
de débito) funciona igual; `account-service`/`yanki-service` solo fallan
al intentar consumir/publicar en sus tópicos.

**Redis también es necesario para `customer-service`, incluso para Parte
I/II**: `GET /api/customers/{id}` (y por lo tanto la apertura de cualquier
cuenta o crédito, que valida al cliente contra ese endpoint) depende de que
la cache Redis (`localhost:6379`) esté accesible — ver Hallazgo 3 en
`docs/verification/test-partI/test-partI.md`. Para levantar solo Redis sin
el resto de Docker Compose: `docker run -d -p 6379:6379 redis:7-alpine`.

```bash
# 1. Eureka Server (primero)
cd eureka-server && mvn spring-boot:run

# 2. Config Server
cd config-server && mvn spring-boot:run

# 3. En terminales distintas, los servicios de negocio
cd customer-service && mvn spring-boot:run
cd account-service  && mvn spring-boot:run
cd credit-service   && mvn spring-boot:run
cd yanki-service    && mvn spring-boot:run

# 4. API Gateway (al final, cuando los anteriores ya esten registrados en Eureka)
cd api-gateway && mvn spring-boot:run
```

### Opción B — Docker Compose (recomendado)

Levanta los 7 microservicios, sus 4 bases MongoDB, Kafka y Redis en
contenedores separados, en el orden correcto (`depends_on` con
healthchecks):

```bash
docker compose up --build
```

Esto activa automáticamente el perfil `docker` de cada microservicio
(variables `SPRING_PROFILES_ACTIVE`/`SPRING_CONFIG_IMPORT` definidas en
`docker-compose.yml`), que resuelve los hostnames internos de Docker
(`eureka-server`, `customer-mongo`, `account-mongo`, `credit-mongo`,
`yanki-mongo`, `kafka`, `customer-redis`, `customer-service`) en vez de
`localhost`, sin tocar el código de los microservicios. El propio contenedor
Docker Compose de `kafka`/`customer-redis` no se probó en vivo en esta
sesión, pero sí se validó en vivo un Redis standalone equivalente (ver
`docs/verification/test-partI/test-partI.md`, Hallazgo 3) y el arranque de
`account-service`/`yanki-service` con Kafka configurado correctamente (ver
Hallazgo 2 del mismo documento); el round-trip completo de mensajes Kafka
sigue sin validarse en vivo, solo con mocks (ver
`docs/verification/fase10-15-parte3-checklist.md`).

### Verificación funcional

El sistema no tiene interfaz gráfica; toda la verificación de Parte I/II
se hace con la colección de Postman en `docs/postman/` (ver su README
para el orden de ejecución recomendado; aún no cubre Parte III). Se puede
apuntar la colección directamente a cada microservicio (puertos
8081/8082/8083/8084) o, preferentemente, al API Gateway en el puerto 8080.
Como registro de que esa verificación ya se hizo al menos una vez con
peticiones HTTP reales (no solo lectura de código), ver
`docs/verification/test-partI/test-partI.md` y
`docs/verification/test-partII/test-partII.md`.

### Pruebas unitarias y cobertura

```bash
cd customer-service && mvn test   # o account-service / credit-service
```

El reporte de Jacoco queda en `target/site/jacoco/index.html` de cada
microservicio.
