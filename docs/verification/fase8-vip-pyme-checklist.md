# Fase 8 — Perfiles VIP/PYME y reglas de cuenta: checklist de verificación

Cotejo de los requisitos de la Fase 8 (Parte II, "Funcionalidades")
contra lo implementado.

| Requisito | Estado | Evidencia |
|---|---|---|
| Las cuentas bancarias tienen un monto mínimo de apertura, que puede ser cero | ✅ | `BankAccount.minimumOpeningAmount` (`@PositiveOrZero`, admite 0); validado en `AccountServiceImpl.validateAndPrepare` contra el `balance` inicial indicado al crear la cuenta |
| Nuevo perfil personal **VIP**: cuenta de ahorro con monto mínimo de promedio diario mensual | ✅ | `SavingsAccount.minimumDailyAverageBalance`; exigido y validado (`> 0`) solo cuando `customer.profile() == VIP`, en `SavingsAccountRuleStrategy.validateVipRequirements` |
| VIP requiere tarjeta de crédito previa con el banco | ✅ | `SavingsAccountRuleStrategy` llama a `CreditClient.hasActiveCreditCard(customerId)` (nueva integración `account-service` → `credit-service`, vía `lb://` + circuit breaker Resilience4j de 2s) antes de otorgar la cuenta |
| Nuevo perfil empresarial **PYME**: cuenta corriente sin comisión de mantenimiento | ✅ | `CheckingAccountRuleStrategy.validatePymeRequirements` fuerza `maintenanceFee = 0` cuando `customer.profile() == PYME`, sin importar el valor enviado en el request |
| PYME requiere tarjeta de crédito previa con el banco | ✅ | Misma validación vía `CreditClient.hasActiveCreditCard`, reutilizando la integración agregada para VIP |
| Todas las cuentas tienen un número máximo de transacciones sin comisión; superado ese número se cobra comisión por transacción | ✅ | `BankAccount.freeMonthlyTransactionLimit` + `transactionFeeAmount` (comunes a los 3 tipos de cuenta); `AccountServiceImpl.applyMovementWithPossibleFee` cuenta las transacciones (depósitos + retiros, excluyendo comisiones ya cobradas) del mes en curso y, si se alcanza el límite, registra un movimiento adicional `MovementType.FEE` |

## Decisiones de diseño relevantes

- **El perfil (`CustomerProfile`) vive en `customer-service`, no en
  `account-service`.** `account-service` solo lo consulta a través de
  `CustomerDto.profile()` (igual que ya hacía con `customerType`), para
  no duplicar la fuente de verdad del dato.
- **VIP/PYME no son subtipos nuevos de cuenta.** Se decidió no crear
  `VipSavingsAccount`/`PymeCheckingAccount` como subclases separadas,
  sino tratar VIP y PYME como *comportamiento condicionado por el perfil
  del cliente* sobre los mismos `SavingsAccount`/`CheckingAccount` de la
  Parte I. Esto evita duplicar la regla de "una sola cuenta de ahorro por
  cliente personal" contra dos jerarquías de clases distintas, y encaja
  naturalmente con el patrón Strategy ya existente (un método más en la
  misma estrategia, no una estrategia nueva).
- **La verificación de tarjeta de crédito previa es una llamada HTTP
  real a `credit-service`**, no una consulta directa a su base de datos
  (`GET /api/credits/customers/{customerId}/has-credit-card`, nuevo
  endpoint). Se respeta así el patrón *database per service* también
  para esta nueva regla cruzada.
- **La comisión por exceso de transacciones es una regla común a los 3
  tipos de cuenta**, aplicada en `AccountServiceImpl` (no en las
  estrategias por tipo), porque a diferencia de los límites "duros" de
  la Parte I (que sí son propios de cada tipo: límite mensual de ahorro,
  único movimiento de plazo fijo), esta regla es transversal según la
  consigna ("todas las cuentas bancarias tendrán...").
- **La comisión nunca bloquea la transacción.** A diferencia de los
  límites duros de la Parte I, superar `freeMonthlyTransactionLimit`
  solo agrega un cargo adicional; el depósito/retiro solicitado se
  registra igual. Esto es consistente con la redacción de la consigna
  ("se cobrará comisión", no "se rechazará").
- **`existsByCustomerIdAndStatus(customerId, ACTIVE)` en
  `CreditCardRepository`** se eligió sobre "cualquier tarjeta, sin
  importar el estado", para que una tarjeta cancelada/cerrada no cuente
  como requisito cumplido; misma interpretación ya aplicada en la Parte I
  para el crédito personal único activo.

## Pruebas agregadas

- `SavingsAccountRuleStrategyTest`: 3 escenarios VIP nuevos (rechazo sin
  promedio diario, rechazo sin tarjeta previa, apertura exitosa).
- `CheckingAccountRuleStrategyTest`: 2 escenarios PYME nuevos (rechazo
  sin tarjeta previa, apertura exitosa con `maintenanceFee` forzado a 0).
- `AccountServiceImplTest`: monto mínimo de apertura (rechazo y caso
  límite en 0) y comisión por exceso de transacciones (con y sin cobro,
  y verificación del balance final tras ambos movimientos).
- `CreditServiceImplTest`: `hasActiveCreditCard` delega correctamente en
  el repositorio.
- Colección Postman: carpeta dedicada "4. Fase 8" con los mismos
  escenarios de extremo a extremo contra microservicios reales.
