# Contratos OpenAPI — Sistema Bancario (Microservicios)

Este directorio consolida los 4 contratos OpenAPI de la solución. Cada
microservicio mantiene su propia copia autoritativa en
`src/main/resources/static/openapi/<servicio>.yaml`, expuesta también vía
Swagger UI en tiempo de ejecución; los archivos aquí son una copia de
referencia para revisión conjunta y no deben editarse directamente (edite
el original dentro de cada microservicio y vuelva a copiar).

| Microservicio | Puerto | Contrato | Swagger UI (local) |
|---|---|---|---|
| customer-service | 8081 | [customer-service.yaml](./customer-service.yaml) | http://localhost:8081/webjars/swagger-ui/index.html |
| account-service | 8083 | [account-service.yaml](./account-service.yaml) | http://localhost:8083/webjars/swagger-ui/index.html |
| credit-service | 8082 | [credit-service.yaml](./credit-service.yaml) | http://localhost:8082/webjars/swagger-ui/index.html |
| yanki-service | 8084 | [yanki-service.yaml](./yanki-service.yaml) | http://localhost:8084/webjars/swagger-ui/index.html |

## Resumen de recursos

### customer-service — `/api/customers`
CRUD de clientes personales y empresariales (`Customer`, discriminado por
`customerType`), más `/api/auth/login` y
`POST /api/customers/{id}/credentials` (login JWT, Fase 13).

### account-service — `/api/accounts`
CRUD de cuentas bancarias (`SavingsAccount`, `CheckingAccount`,
`FixedTermAccount`, discriminadas por `accountType`), más:
- `GET /api/accounts/{id}/balance`
- `POST /api/accounts/{id}/deposits`
- `POST /api/accounts/{id}/withdrawals`
- `GET /api/accounts/{id}/movements`
- `POST /api/accounts/{id}/transfers`, `GET .../movements/report` (Fase 9)
- `/api/debit-cards` — tarjetas de débito, 1:1 con una cuenta (Fase 11)

### credit-service — `/api/credits`
CRUD de productos de crédito (`PersonalCredit`, `BusinessCredit`,
`CreditCard`, discriminados por `creditType`), más:
- `GET /api/credits/{id}/balance`
- `POST /api/credits/{id}/payments` (admite `payerCustomerId`, Fase 10)
- `POST /api/credits/{id}/consumptions`
- `GET /api/credits/{id}/movements`, `.../movements/report`, `.../movements/last-10` (Fase 9)
- `GET /api/credits/customers/{customerId}/has-overdue-debt` (Fase 10)

### yanki-service — `/api/yanki`
Monedero móvil (Fase 12): `/wallets` (registro, consulta, movimientos),
`/transfers` (envío/recepción por celular), `/wallets/{id}/link-debit-card`,
`/wallets/{id}/load`, `/wallets/{id}/withdraw` (asíncronos vía Kafka con
account-service, responden `202` y se consultan en `/operations/{correlationId}`).

## Convenciones comunes

- Todos los contratos siguen OpenAPI 3.0.3.
- Los payloads polimórficos usan `oneOf` + `discriminator` sobre el campo
  `*Type` (`accountType`, `creditType`, `customerType` cuando aplica).
- Los errores de negocio se representan con `ApiError` (formato RFC 7807 /
  `ProblemDetail`, tal como lo devuelve Spring WebFlux por defecto).
- Los campos `id`, `createdAt` y `updatedAt` son de solo lectura
  (`readOnly: true`), generados por el servidor.
