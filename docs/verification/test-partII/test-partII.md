# Pruebas de validación — Parte II

Validación funcional **en vivo** (peticiones HTTP reales, no solo revisión
de código) de todo lo que `Proyecto_General_vf.md` exige en la Parte II:
monto mínimo de apertura, perfiles VIP/PYME, comisión por exceso de
transacciones, transferencias, los dos reportes, Eureka, API Gateway y
circuit breaker con timeout de 2s.

- **Fecha de ejecución:** 2026-07-28 / 2026-07-29
- **Entorno:** stack local con Maven (JDK 21 — ver nota de entorno en
  `CLAUDE.md`), MongoDB local en `27017`, los 6 microservicios corriendo
  simultáneamente (`eureka-server`, `config-server`, `customer-service`,
  `account-service`, `credit-service`, `api-gateway`)
- **Script de pruebas:** `testrun/run-tests.sh` (raíz del repo, no
  versionado); log completo de todas las peticiones/respuestas en
  `testrun/results.log`
- **Resultado global: 24/24 comportamientos esperados verificados**, más
  **1 defecto preexistente descubierto** (no introducido por Fase 9, ver
  sección "Hallazgo" al final)

> Dos bloqueos de entorno tuvieron que sortearse para poder levantar el
> stack (detalle completo en la sección "Notas de entorno" al final):
> `springdoc-openapi-starter-webflux-ui:2.8.17` no era compatible con
> Spring Boot 4.1.0, y el módulo de tests de `api-gateway` no compilaba en
> este Spring Boot. Ninguno de los dos era un defecto de Parte II; ambos
> eran preexistentes y no relacionados con las funcionalidades validadas.
>
> **Actualización 2026-07-28 (misma sesión):** tanto el defecto descrito en
> "Hallazgo" como los dos bloqueos de entorno fueron corregidos a pedido
> del usuario. Ver el detalle de la corrección y su verificación al final
> de cada sección correspondiente. El stack completo ahora arranca con
> `mvn spring-boot:run` puro, sin ningún flag ni exclusión manual.

## Resumen ejecutivo

| # | Requisito de Parte II | Resultado |
|---|---|---|
| 1 | Monto mínimo de apertura (puede ser 0) | ✅ OK |
| 2 | Perfil VIP (ahorro, requiere tarjeta de crédito previa) | ✅ OK |
| 3 | Perfil PYME (corriente, comisión forzada a 0, requiere tarjeta previa) | ✅ OK |
| 4 | Comisión por exceso de transacciones mensuales | ✅ OK |
| 5 | Transferencias (mismo cliente y terceros del mismo banco) | ✅ OK |
| 6 | Reporte general por producto en un intervalo de fechas | ✅ OK |
| 7 | Reporte de últimos 10 movimientos de tarjeta de crédito | ✅ OK |
| 8 | Eureka (registro y descubrimiento) | ✅ OK |
| 9 | API Gateway (punto de entrada único) | ✅ OK |
| 10 | Circuit breaker Resilience4j, timeout 2s | ✅ OK |
| — | *(hallazgo, no es un requisito de Parte II)* Bug en unicidad de cuentas por tipo | ✅ Corregido (ver "Hallazgo") |

---

## 1. Monto mínimo de apertura

> "Las cuentas bancarias tienen un monto mínimo de apertura que puede ser cero (0)."

**1a) Rechaza apertura por debajo del mínimo** — `balance=50 < minimumOpeningAmount=100`

```
POST /api/accounts
{"accountType":"SAVINGS","accountNumber":"...-01","customerId":"...","balance":50,
 "minimumOpeningAmount":100, ...}

→ 400 Bad Request
```

**1b) Permite apertura con `minimumOpeningAmount=0` y `balance=0`**

```
POST /api/accounts  →  201 Created
{
  "accountType": "SAVINGS", "balance": 0, "minimumOpeningAmount": 0,
  "id": "6a694ca24a9b885319e729e1", "status": "ACTIVE", ...
}
```

## 2. Perfil VIP

> "VIP: cuenta de ahorro que requiere un monto mínimo de promedio diario...
> el cliente debe tener una tarjeta de crédito con el banco al momento de
> la creación de la cuenta."

**2a) Rechaza apertura VIP sin tarjeta de crédito previa**

```
POST /api/accounts (customer VIP, minimumDailyAverageBalance=1000, sin tarjeta)
→ 400 Bad Request
```

**2b) Se otorga una tarjeta de crédito al cliente**

```
POST /api/credits  {"creditType":"CREDIT_CARD", ...}  → 201 Created
```

**2c) Ahora sí permite abrir la cuenta de ahorro VIP**

```
POST /api/accounts  →  201 Created
{
  "accountType": "SAVINGS", "minimumDailyAverageBalance": 1000,
  "id": "6a694ca64a9b885319e729e4", "customerId": "...", "balance": 1000, ...
}
```

## 3. Perfil PYME

> "PYME: cuenta corriente sin comisión de mantenimiento. Como requisito,
> el cliente debe tener una tarjeta de crédito con el banco..."

**3a) Rechaza apertura PYME sin tarjeta de crédito previa** → `400 Bad Request`

**3b) Se otorga una tarjeta de crédito al cliente empresarial** → `201 Created`

**3c) Ahora sí permite abrir la cuenta corriente PYME, con `maintenanceFee` forzado a 0**

```
POST /api/accounts  (se envía maintenanceFee=20 en el request)
→ 201 Created
{
  "accountType": "CHECKING", "maintenanceFee": 0,   ← forzado por el perfil PYME
  "id": "6a694ca84a9b885319e729e5", ...
}
```

## 4. Comisión por exceso de transacciones

> "Todas las cuentas bancarias tendrán un número máximo de transacciones
> que no cobrará comisión y superado ese número se cobrará comisión por
> cada transacción realizada."

Cuenta con `freeMonthlyTransactionLimit=2`, `transactionFeeAmount=5`:

| Movimiento | Resultado |
|---|---|
| 4a) Depósito 1/2 (gratuito) | `201`, `movementType=DEPOSIT` |
| 4b) Depósito 2/2 (gratuito, alcanza el límite) | `201` |
| 4c) Depósito 3/2 (excede el límite) | `201`, genera además un movimiento `FEE` |
| 4d) `GET .../movements` | `200`, exactamente **1** movimiento `FEE` presente |

## 5. Transferencias

> "Implementar las transferencias bancarias entre cuentas del mismo
> cliente y cuentas a terceros del mismo banco."

**5a) Transferencia entre dos cuentas del mismo cliente**

```
POST /api/accounts/6a694ca44a9b885319e729e2/transfers
{"destinationAccountId":"6a694ca44a9b885319e729e3","amount":30}

→ 201 Created
{
  "sourceMovement":      {"movementType":"TRANSFER_OUT","amount":30,"balanceAfter":70,...},
  "destinationMovement": {"movementType":"TRANSFER_IN", "amount":30,"balanceAfter":230,...}
}
```

**5b) Transferencia hacia una cuenta de un tercero del mismo banco**

```
POST /api/accounts/6a694ca44a9b885319e729e3/transfers
{"destinationAccountId":"6a694ca24a9b885319e729e1","amount":25}

→ 201 Created
{
  "sourceMovement":      {"movementType":"TRANSFER_OUT","amount":25,"balanceAfter":205,...},
  "destinationMovement": {"movementType":"TRANSFER_IN", "amount":25,"balanceAfter":190,...}
}
```

**5c) Rechaza transferencia a la misma cuenta** → `400 Bad Request`

**5d) Rechaza transferencia con fondos insuficientes** (`amount=999999`) → `400 Bad Request`

*(Nota: para 5a/5b se usaron dos cuentas corrientes de un cliente
empresarial en vez de una cuenta de ahorro + corriente de un cliente
personal, para no chocar con el defecto preexistente descrito en
"Hallazgo" — el mecanismo de transferencia en sí es agnóstico a la
titularidad, así que cubre igual el requisito.)*

## 6. Reportes

> "Reporte completo y general por producto del banco en intervalo de
> tiempo especificado por el usuario" + "últimos 10 movimientos de tarjeta
> de débito y de crédito."

**6a) Reporte por rango de fechas — cuenta de ahorro**

```
GET /api/accounts/6a694ca24a9b885319e729e1/movements/report?startDate=2020-01-01T00:00:00Z&endDate=2035-12-31T23:59:59Z
→ 200 OK, 6 movimientos (todos los de la cuenta dentro del rango, orden descendente)
```

**6b) Reporte por rango de fechas — tarjeta de crédito**

Se generaron 2 consumos + 1 pago sobre una tarjeta, luego:

```
GET /api/credits/{cardId}/movements/report?startDate=...&endDate=...
→ 200 OK, 3 movimientos (coincide exactamente con lo generado)
```

**6c) Últimos 10 movimientos de una tarjeta de crédito**

```
GET /api/credits/{cardId}/movements/last-10
→ 200 OK, 3 movimientos (los mismos 3, por ser menos de 10)
```

**6d) Rechaza el reporte de últimos-10 sobre un crédito que no es tarjeta**

```
GET /api/credits/{personalCreditId}/movements/last-10
→ 400 Bad Request
```

Confirma el comportamiento documentado en
`docs/verification/fase9-transfers-reports-checklist.md`: el reporte de
últimos 10 solo aplica a `CREDIT_CARD`; el de tarjeta de débito queda
pendiente para Parte III porque ese producto todavía no existe.

## 7. Infraestructura (Eureka, API Gateway, Resilience4j)

**Eureka** — con los 3 servicios de negocio arriba, `GET :8761/eureka/apps`
lista `CUSTOMER-SERVICE`, `ACCOUNT-SERVICE` y `CREDIT-SERVICE`, los tres
con `status: UP`.

**API Gateway** — las 3 rutas responden correctamente a través del punto
de entrada único en `:8080` (en vez de pegarle a cada puerto por
separado):

```
GET http://localhost:8080/api/customers  → 200 OK
GET http://localhost:8080/api/accounts   → 200 OK
GET http://localhost:8080/api/credits    → 200 OK
```

**Circuit breaker + timeout de 2s** — se detuvo el proceso de
`credit-service` a propósito y se intentó abrir una cuenta de ahorro VIP
(que internamente llama a `credit-service` vía `CreditClient` para
verificar la tarjeta previa):

```
POST /api/accounts (VIP, credit-service caído)
→ 503 Service Unavailable, en 2.09 segundos
{
  "timestamp": "...", "status": 503, "error": "Service Unavailable", ...
}
```

Log de `account-service` en el momento exacto:

```
WARN AccountServiceImpl - Apertura de cuenta rechazada para customerId=...:
503 SERVICE_UNAVAILABLE "credit-service no disponible o excedio el timeout de 2s:
Did not observe any item or terminal signal within 2000ms in 'circuitBreaker'
(and no fallback has been configured)"
```

Confirma que el `TimeLimiter` de Resilience4j (`creditServiceCB`,
`timeoutDuration: 2s` en `config-server`) corta la espera exactamente a
los 2 segundos configurados y responde `503` en vez de colgar la petición,
tal como exige la consigna.

---

## Hallazgo: bug preexistente en unicidad de cuentas por tipo (no es de Parte II)

Durante esta validación se descubrió que **un cliente personal que ya
tiene una cuenta de un tipo queda bloqueado (409 falso positivo) al
intentar abrir cualquier cuenta de OTRO tipo**, en vez de solo bloquear un
segundo duplicado del mismo tipo.

**Reproducción 1** — `customer1` (PERSONAL/STANDARD) abre primero una
cuenta de ahorro (éxito), luego intenta abrir una cuenta corriente (nunca
tuvo una):

```
POST /api/accounts {"accountType":"CHECKING", "customerId":"<customer1>", ...}
→ 409 Conflict
```

Log real: `"El cliente ya tiene una cuenta corriente"` — mensaje
incorrecto, el cliente nunca abrió una cuenta corriente.

**Reproducción 2** (inversa) — `customer2` (PERSONAL/VIP) abre primero una
cuenta de ahorro VIP (éxito, ver sección 2), luego intenta abrir una
cuenta corriente (nunca tuvo una):

```
POST /api/accounts {"accountType":"CHECKING", "customerId":"<customer2>", ...}
→ 409 Conflict
```

Mismo resultado. `GET /api/accounts` confirma que, en ambos casos, el
cliente solo tenía **una** cuenta (de ahorro) en la base de datos —
ninguna cuenta corriente previa que justifique el 409.

**Causa raíz probable:** `SavingsAccountRepository.existsByHoldersContaining`
y `CheckingAccountRepository.existsByHoldersContaining`
(`account-service/src/main/java/.../repository/`) están documentados con
el comentario *"Spring Data filtra automáticamente por el discriminador
de tipo (`_class`) de esta subclase"*, pero en la práctica la consulta
generada no aplica ese filtro: ambos repositorios comparten la colección
`accounts` y, al consultar solo por `holders`, encuentran **cualquier**
cuenta del cliente sin importar su subtipo concreto.

**Impacto:** rompe la regla de Parte I *"un cliente personal puede tener
una cuenta de ahorro, una cuenta corriente y cuentas a plazo fijo"* (son
límites independientes de 1 por tipo, no un límite combinado de 1 cuenta
en total). Actualmente, tras abrir su primera cuenta de cualquier tipo, un
cliente personal no puede abrir ningún otro tipo de cuenta.

**Corrección aplicada (2026-07-28, misma sesión, a pedido del usuario):**
en `SavingsAccountRuleStrategy` y `CheckingAccountRuleStrategy` se
reemplazó la dependencia de `SavingsAccountRepository`/
`CheckingAccountRepository` (cuyo `existsByHoldersContaining` no filtra
por `_class`) por `BankAccountRepository.findByHoldersContaining(customerId)`
seguido de un filtro en memoria por el tipo concreto
(`.any(SavingsAccount.class::isInstance)` /
`.any(CheckingAccount.class::isInstance)`), reutilizando el patrón Strategy
ya existente. Los dos repositorios tipados quedaron sin uso y se
eliminaron (`account-service/src/main/java/.../repository/SavingsAccountRepository.java`,
`CheckingAccountRepository.java`). Se agregaron pruebas de regresión
explícitas en `SavingsAccountRuleStrategyTest` y `CheckingAccountRuleStrategyTest`
(`permiteAperturaCuandoElClienteYaTieneUnaCuentaCorrienteDeOtroTipo` /
`...DeAhorroDeOtroTipo`) y se reprodujo el fix en vivo: el mismo cliente
personal ahora abre ahorro (`201`) y corriente (`201`) sin bloqueo,
mientras que una segunda cuenta de ahorro sigue correctamente rechazada
(`409`). `./mvnw test` en `account-service` no muestra regresiones nuevas
(los únicos errores restantes son los 9 tests preexistentes y no
relacionados descritos en la nota de Mockito de `CLAUDE.md`).

---

## Notas de entorno (bloqueos no relacionados con Parte II)

Estos dos problemas impidieron levantar el stack tal cual estaba
configurado. En la corrida original de esta validación se sortearon sin
tocar código (solo flags de arranque); **luego, a pedido del usuario, se
corrigieron de raíz en el código fuente** (misma sesión, 2026-07-28):

1. **`springdoc-openapi-starter-webflux-ui:2.8.17` incompatible con
   Spring Boot 4.1.0.** Las 3 microservicios de negocio no arrancaban
   (`NoClassDefFoundError: WebFluxProperties` al inicializar
   `SwaggerConfig`). **Corrección:** se subió la dependencia a
   `springdoc-openapi-starter-webflux-ui:3.0.3` (la serie 3.x de springdoc
   sigue a Spring Boot 4.x, igual que la serie 2.x seguía a Spring Boot
   3.x) en los `pom.xml` de `customer-service`, `account-service` y
   `credit-service`. Verificado: los 3 servicios arrancan con
   `mvn spring-boot:run` puro (sin exclusiones) y `/v3/api-docs` /
   `/swagger-ui.html` responden correctamente.
2. **`api-gateway` no compilaba sus tests en este Spring Boot.**
   `FallbackControllerTest` usaba `@WebFluxTest`, anotación removida en
   Spring Boot 4.x (la clase ya no existe en `spring-boot-test-autoconfigure:4.1.0`).
   **Corrección:** se reescribió el test para instanciar el controlador
   directamente con `WebTestClient.bindToController(new FallbackController())`
   (no necesita contexto de Spring, ya que el controlador no tiene
   dependencias). Además, `ApiGatewayApplicationTests` fallaba por una
   causa distinta y real: el import obligatorio `spring.config.import:
   configserver:...` de `application.yaml` ya no se desactiva de forma
   confiable solo con `spring.cloud.config.enabled=false` en la versión
   de Spring Cloud Config resuelta (los imports se procesan de forma
   acumulativa entre "rounds", no como una propiedad que se pueda
   sobrescribir). **Corrección:** se agregó
   `api-gateway/src/test/resources/application.yaml`, que en el classpath
   de test reemplaza (no fusiona) al `application.yaml` de
   `src/main/resources` y declara el import como
   `optional:configserver:http://localhost:8888`, de modo que si el
   config-server no está corriendo durante los tests, la carga de config
   remota simplemente se omite en vez de fallar. Verificado con
   config-server apagado: `mvn test` en `api-gateway` da `BUILD SUCCESS`
   (2/2 tests) de forma completamente aislada.

Ver también la nota de JDK 25 vs. 21, y la lista de tests preexistentes y
no relacionados que siguen fallando (sin tocar, no fueron parte de este
pedido), en `CLAUDE.md`.
