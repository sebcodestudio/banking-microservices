# Pruebas de validación — Parte I

Validación funcional **en vivo** (peticiones HTTP reales, no solo revisión de
código) de todo lo que `Proyecto_General_vf.md` Parte I exige: CRUD de
clientes (personal/empresarial), CRUD de cuentas bancarias (ahorro/corriente/
plazo fijo) con sus reglas de unicidad y límites, CRUD de créditos (personal/
empresarial/tarjeta) con sus reglas de unicidad, depósitos/retiros/pagos/
consumos, y consulta de saldos y movimientos.

Hasta esta corrida, lo único que existía para Parte I era revisión estática
de código (`business-rules-checklist.md`, `code-review-checklist.md`,
`fase7-infrastructure-checklist.md`): cotejo contra la consigna y greps sobre
el código fuente, sin ejecutar un solo request real. Este documento cubre
ese vacío, con el mismo criterio con el que ya se validó la Parte II
(`docs/verification/test-partII/test-partII.md`).

- **Fecha de ejecución:** 2026-07-29 / 2026-07-30
- **Entorno:** stack local con Maven (JDK 21 — ver nota de entorno en
  `CLAUDE.md`), MongoDB local en `27017`, los 3 microservicios de negocio +
  `eureka-server` + `config-server` corriendo simultáneamente. Además fue
  necesario levantar un contenedor Redis local (`docker run -p 6379:6379
  redis:7-alpine`, no forma parte del `docker-compose.yml` del repo, se creó
  ad-hoc solo para esta corrida) — ver "Hallazgo 3" abajo sobre por qué
  terminó siendo un requisito duro incluso para funcionalidad de Parte I.
- **Script de pruebas:** `testrun/run-tests-partI.sh` (raíz del repo, no
  versionado, mismo patrón que `testrun/run-tests.sh` de la Parte II); log
  completo de todas las peticiones/respuestas en `testrun/results-partI.log`
- **Resultado global: 26/26 comportamientos esperados verificados**, tras
  corregir **3 defectos reales descubiertos durante la validación** (ninguno
  de Parte I específicamente — los tres son fallas de la base común que
  bloqueaban probar Parte I en un stack recién levantado; detalle completo
  en la sección "Hallazgos" al final)

## Resumen ejecutivo

| # | Requisito de Parte I | Resultado |
|---|---|---|
| 1 | CRUD de clientes (personal y empresarial) | ✅ OK |
| 2 | Cliente personal: máx. 1 ahorro, 1 corriente, N plazo fijo | ✅ OK |
| 3 | Cliente empresarial: sin ahorro/plazo fijo, múltiples corrientes | ✅ OK |
| 4 | Cuentas empresariales: titulares y firmantes autorizados | ✅ OK |
| 5 | Depósitos, retiros, saldo, movimientos | ✅ OK |
| 6 | Límite máximo de movimientos mensuales (ahorro) | ✅ OK |
| 7 | Plazo fijo: un único movimiento en un día específico del mes | ✅ OK |
| 8 | CRUD completo de cuentas (update/delete) | ✅ OK |
| 9 | Crédito personal: solo uno por persona | ✅ OK |
| 10 | Crédito empresarial: varios por empresa | ✅ OK |
| 11 | Tarjeta de crédito (personal o empresarial) | ✅ OK |
| 12 | Crédito sin necesidad de cuenta bancaria | ✅ OK |
| 13 | Pagos y consumos (respetando límite disponible) | ✅ OK |
| 14 | CRUD completo de créditos (update/delete) | ✅ OK |
| — | *(hallazgo, no es un requisito de Parte I)* Jackson ignora los valores por defecto de campos `@NotNull` al deserializar un POST | ✅ Corregido |
| — | *(hallazgo)* `account-service`/`yanki-service` no arrancan: falta autoconfiguración de Kafka | ✅ Corregido |
| — | *(hallazgo)* Cache Redis de `customer-service` rompe `GET /api/customers/{id}` (y en cascada, apertura de cuentas/créditos) | ✅ Corregido |

---

## 1. CRUD de clientes

```
POST /api/customers  {"customerType":"PERSONAL","documentNumber":"P1-...","email":"p1@test.com","firstName":"Juan","lastName":"Perez","birthDate":"1990-01-01"}
→ 201 Created
{"id":"...", "profile":"STANDARD", "status":"ACTIVE", ...}
```

Nótese que el request **no envía `profile` ni `status`** — la respuesta debe
completarlos con sus valores por defecto (`STANDARD`/`ACTIVE`); esto solo
funciona luego del fix descrito en "Hallazgo 1".

- `POST /api/customers` (empresarial) → `201 Created`
- `GET /api/customers` → `200 OK`, incluye ambos clientes recién creados
- `GET /api/customers/{id}` → `200 OK`
- `PUT /api/customers/{id}` → `200 OK`, datos actualizados
- `POST` + `DELETE /api/customers/{id}` → `204 No Content`
- `GET /api/customers/{id}` (tras el delete) → `404 Not Found`

## 2. Cuentas bancarias — cliente personal

**2a/2b) Abre cuenta de ahorro y cuenta corriente** → ambas `201 Created`

**2c) Rechaza una segunda cuenta de ahorro del mismo cliente**

```
POST /api/accounts {"accountType":"SAVINGS", "customerId":"<mismo cliente>", ...}
→ 409 Conflict
```

**2d) Rechaza una segunda cuenta corriente del mismo cliente** → `409 Conflict`

**2e) Permite múltiples cuentas a plazo fijo del mismo cliente**

```
POST /api/accounts {"accountType":"FIXED_TERM", ...} → 201 Created  (primera)
POST /api/accounts {"accountType":"FIXED_TERM", ...} → 201 Created  (segunda, mismo cliente)
```

Confirma explícitamente la regla de Parte I: el límite de "una cuenta de
ahorro, una corriente" **no aplica** a plazo fijo (que admite N).

## 3. Cuentas bancarias — cliente empresarial

**3a) Rechaza cuenta de ahorro para cliente empresarial** → `400 Bad Request`

**3b) Rechaza cuenta a plazo fijo para cliente empresarial** → `400 Bad Request`

**3c) Permite múltiples cuentas corrientes, con titulares y firmantes autorizados**

```
POST /api/accounts
{"accountType":"CHECKING","customerId":"<empresa>","holders":["<empresa>"],
 "authorizedSigners":["<otro cliente>"], ...}
→ 201 Created

POST /api/accounts  (segunda cuenta corriente, mismo cliente empresarial)
→ 201 Created
```

## 4. Depósitos, retiros, saldo y movimientos

| Operación | Resultado |
|---|---|
| `POST .../deposits` (100) | `201`, `balanceAfter=150` |
| `POST .../withdrawals` (30) | `201`, `balanceAfter=120` |
| `GET .../balance` | `200`, `balance=120` |
| `GET .../movements` | `200`, 2 movimientos (orden descendente) |
| `POST .../withdrawals` (999999, fondos insuficientes) | `400 Bad Request` |

## 5. Límite máximo de movimientos mensuales — cuenta de ahorro

> "Todas las cuentas bancarias tendrán un número máximo de movimientos
> mensuales" (ahorro: límite duro, no solo comisión — distinto del límite de
> Parte II que solo agrega comisión).

Cuenta con `monthlyMovementLimit=2`:

| Movimiento | Resultado |
|---|---|
| Depósito 1/2 | `201 Created` |
| Depósito 2/2 | `201 Created` |
| Depósito 3/2 | `400 Bad Request` — "Se alcanzó el límite de movimientos mensuales de la cuenta de ahorro" |

## 6. Cuenta a plazo fijo — un único movimiento en un día específico del mes

Cuenta abierta con `specificMovementDay` = día actual del mes (29):

| Caso | Resultado |
|---|---|
| Depósito el día correcto | `201 Created` |
| Segundo movimiento, mismo período (mismo día) | `400 Bad Request` — ya se usó el único movimiento |
| Depósito en una cuenta con `specificMovementDay` distinto al día de hoy | `400 Bad Request` |

## 7. Update / Delete de cuentas

- `PUT /api/accounts/{id}` → `200 OK` (reemplazo completo; campos omitidos
  como `holders`/`openingDate` quedan `null`, semántica estándar de PUT)
- `DELETE /api/accounts/{id}` → `204 No Content`
- `GET /api/accounts/{id}` (tras el delete) → `404 Not Found`

## 8. CRUD de créditos y reglas de negocio

**8a/8b) Crédito personal: solo uno por persona**

```
POST /api/credits {"creditType":"PERSONAL", "customerId":"<cliente>", ...} → 201 Created
POST /api/credits {"creditType":"PERSONAL", "customerId":"<mismo cliente>", ...} → 409 Conflict
```

**8c) Crédito empresarial: se permiten varios para la misma empresa**

```
POST /api/credits {"creditType":"BUSINESS", ...} → 201 Created  (primero)
POST /api/credits {"creditType":"BUSINESS", ...} → 201 Created  (segundo, misma empresa)
```

**8d/8e) Tarjeta de crédito, y crédito sin necesidad de cuenta bancaria**

```
POST /api/credits {"creditType":"CREDIT_CARD","customerId":"<cliente sin ninguna cuenta>", ...}
→ 201 Created
```

Se confirmó contra `GET /api/accounts` que ese cliente no tenía ninguna
cuenta bancaria registrada — el alta del crédito igual funcionó, cumpliendo
"un cliente puede tener un producto de crédito sin la obligación de tener
una cuenta bancaria".

**8f/8g) Consumo de tarjeta dentro y fuera del límite disponible**

| Consumo | Resultado |
|---|---|
| 500 (límite 3000, disponible 3000) | `201 Created`, `availableLimit=2500` |
| 999999 (excede el disponible) | `400 Bad Request` |

**8h) Pago de crédito personal reduce el balance**

```
POST /api/credits/{id}/payments {"amount":250}
→ 201 Created, balanceAfter=4750
GET /api/credits/{id}/balance → balance=4750
```

**8i) Consulta de movimientos** → `200 OK`, coincide con lo generado.

**8j) Update / Delete de crédito** → `200 OK` / `204 No Content` /
`GET` posterior → `404 Not Found`.

---

## Hallazgos

Los siguientes 3 defectos se descubrieron al intentar levantar el stack y
correr esta validación en vivo — ninguno es específico de Parte I (afectan
la base común de los 3 servicios), pero **bloqueaban completamente** poder
probar Parte I sin corregirlos primero. Se corrigieron a medida que
aparecieron, en la misma sesión.

### Hallazgo 1: Jackson ignora los valores por defecto de campos `@NotNull` al deserializar un POST

**Síntoma:** `POST /api/customers` sin enviar `profile` (que debería
completarse con `STANDARD` por defecto) devolvía `400 Bad Request` sin
detalle útil en el cuerpo de la respuesta. Lo mismo ocurría con
`BankAccount.status`/`balance`/`minimumOpeningAmount` y
`CreditProduct.balance`/`status` — cualquier POST que confiara en el valor
por defecto documentado en el modelo, en vez de enviarlo explícitamente,
fallaba.

**Causa raíz:** las entidades (`Customer`, `BankAccount`, `CreditProduct`,
`DebitCard`, `YankiWallet`, `YankiOperation`) declaran
`@NoArgsConstructor` + `@AllArgsConstructor` + `@Builder`. Con
`jackson-module-parameter-names` activo (registrado por defecto en Spring
Boot), Jackson detecta el único constructor público con argumentos como
creador implícito y lo usa en vez del constructor sin argumentos + setters,
pasando `null` en cualquier propiedad ausente del JSON — pisando así el
valor por defecto del campo (`private CustomerProfile profile =
CustomerProfile.STANDARD`, por ejemplo), que solo se aplica en el
constructor sin argumentos. Es un gotcha real y conocido de Jackson +
Lombok, no un defecto de este proyecto en particular, pero nadie lo había
notado porque las pruebas unitarias mockean el repositorio (nunca pasan por
Jackson) y las corridas anteriores de validación en vivo siempre enviaron
esos campos explícitamente en el JSON.

**Corrección:** en las 6 clases afectadas, se quitó `@Builder.Default` de
los campos con valor por defecto (un simple inicializador de campo sí se
ejecuta en cualquier constructor) y se bajó la visibilidad del constructor
con todos los argumentos a `package-private`
(`@AllArgsConstructor(access = AccessLevel.PACKAGE)`): `@Builder` lo sigue
usando internamente (está en el mismo archivo), pero Jackson ya no lo ve
como candidato público y vuelve a deserializar via el constructor sin
argumentos + setters. Verificado en vivo: `POST /api/customers` sin
`profile` ahora responde `201` con `"profile":"STANDARD"`.

Se descubrió una regresión de un test unitario al aplicar este fix
(`CreditCardGrantStrategyTest.permiteUnConsumoDentroDelLimiteDisponible`,
que construía un `CreditCard` de prueba sin `.balance(...)` confiando en el
`@Builder.Default` recién quitado) y se corrigió agregando
`.balance(BigDecimal.ZERO)` explícito al builder del test.

### Hallazgo 2: `account-service` y `yanki-service` no arrancan — falta autoconfiguración de Kafka

**Síntoma:** `account-service` fallaba al arrancar con
`UnsatisfiedDependencyException: ... required a bean of type
'org.springframework.kafka.core.KafkaTemplate' that could not be found`.

**Causa raíz:** la combinación resuelta de `spring-boot-autoconfigure:4.1.0`
+ `spring-kafka:4.1.0` no registra ningún bean de Kafka (se verificó
extrayendo el jar de `spring-boot-autoconfigure` y confirmando que no
contiene ninguna clase relacionada con Kafka) — a diferencia de Redis o
Mongo, el soporte de Spring Boot para Kafka simplemente no está presente en
esta combinación de versiones. Como Kafka/Redis solo se habían validado con
mocks hasta ahora (ver `fase10-15-parte3-checklist.md`), este bloqueo nunca
se había manifestado.

**Corrección:** se agregó `config/KafkaConfig.java` en `account-service` y
en `yanki-service`, definiendo a mano los beans
`ProducerFactory`/`KafkaTemplate`/`ConsumerFactory`/
`ConcurrentKafkaListenerContainerFactory` a partir de las mismas propiedades
`spring.kafka.*` que ya servía `config-server` (sin cambiar ese contrato de
configuración). Verificado: ambos servicios arrancan correctamente incluso
sin un broker Kafka corriendo (el listener container reintenta en segundo
plano, tal como se espera).

### Hallazgo 3: la cache Redis de `customer-service` rompe `GET /api/customers/{id}` y se propaga a `account-service`/`credit-service`

**Síntoma:** `GET /api/customers/{id}` para un cliente recién creado con
`birthDate` devolvía `500 Internal Server Error`. Como `account-service` y
`credit-service` validan la existencia del cliente contra
`GET /api/customers/{id}` en cada apertura de cuenta o crédito, el error se
propagaba como `503 Service Unavailable` (el `CircuitBreaker`/`TimeLimiter`
de 2s se activaba) — es decir, **una cuenta bancaria de Parte I no se podía
abrir** por un defecto ajeno a Parte I.

**Causa raíz:** `RedisConfig.customerRedisTemplate` construye su
`Jackson2JsonRedisSerializer` con `new
Jackson2JsonRedisSerializer<>(Customer.class)`, que internamente crea su
**propio** `ObjectMapper` (no el autoconfigurado por Spring Boot, que sí
trae `jackson-datatype-jsr310` registrado). Al intentar poblar la cache tras
un miss, la serialización de `Customer.birthDate` (`LocalDate`) fallaba con
`InvalidDefinitionException: Java 8 date/time type LocalDate not supported
by default`.

**Corrección:** se cambió el constructor a `new
Jackson2JsonRedisSerializer<>(objectMapper, Customer.class)`, inyectando el
`ObjectMapper` autoconfigurado de Spring Boot (bean ya presente en el
contexto). Verificado en vivo: `GET /api/customers/{id}` responde `200` en
el primer intento (cache miss + populate) y en el segundo (cache hit).

**Nota de diseño (no corregida, fuera de alcance de esta validación):** con
este fix, la cache ya funciona correctamente *cuando Redis está disponible*,
pero `findById` sigue sin tener una ruta de degradación si Redis está caído
o inalcanzable — hoy en día, si Redis no responde, la búsqueda de un
cliente por id falla por completo en vez de recurrir directamente a
MongoDB. Esto convierte a Redis en un punto único de falla incluso para la
funcionalidad básica de Parte I (a través de las validaciones cruzadas de
`account-service`/`credit-service`), lo cual contradice el propósito de una
cache (acelerar, nunca ser un requisito duro). Se deja anotado para una
futura sesión si se considera relevante corregirlo.

---

## Verificación de regresión

Tras aplicar los 3 fixes, se corrió `mvnw test` (JDK 21) en los 4 servicios
afectados:

| Servicio | Resultado |
|---|---|
| `customer-service` | 15/15 tests, 0 fallos |
| `yanki-service` | 15/15 tests, 0 fallos |
| `credit-service` | 26 tests, 1 fallo — el mismo preexistente y no relacionado ya documentado en `CLAUDE.md` (`payRechazaMontoNegativoOCeroSinConsultarElRepositorio`) |
| `account-service` | 52 tests, 9 fallos — los mismos 9 preexistentes y no relacionados ya documentados en `CLAUDE.md` |

Ningún test nuevo quedó roto (la única regresión detectada,
`CreditCardGrantStrategyTest.permiteUnConsumoDentroDelLimiteDisponible`, se
corrigió en el mismo paso, ver "Hallazgo 1").

## Entorno auxiliar usado solo para esta corrida

Se levantó un contenedor Redis ad-hoc (`docker run -d -p 6379:6379
redis:7-alpine`, nombre `testrun-redis`) porque, tras el Hallazgo 3,
`customer-service` necesita Redis disponible para que `findById` no falle.
No se agregó al `docker-compose.yml` del repo (ya tiene su propio servicio
`customer-redis` para el flujo de `docker compose up`); este contenedor
suelto puede detenerse con `docker stop testrun-redis` sin afectar nada más.
