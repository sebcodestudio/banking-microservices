# Fase 9 — Transferencias y reportes (funcionalidades pendientes de Parte II)

Cotejo de los dos puntos de `Proyecto_General_vf.md` (Parte II) que
quedaban sin implementar tras la Fase 8 (perfiles VIP/PYME y comisión por
exceso de transacciones): transferencias bancarias y los dos reportes.

## Transferencias bancarias

> "Implementar las transferencias bancarias entre cuentas del mismo
> cliente y cuentas a terceros del mismo banco."

| Requisito | Estado | Evidencia |
|---|---|---|
| Transferencia entre cuentas del mismo cliente | ✅ | `POST /api/accounts/{id}/transfers`; no distingue titularidad, solo mueve fondos entre dos `BankAccount` de `account-service` |
| Transferencia hacia cuenta de un tercero del mismo banco | ✅ | Mismo endpoint; la cuenta destino puede pertenecer a cualquier `customerId` |
| Reglas por tipo de cuenta aplicadas en ambos extremos | ✅ | `AccountServiceImpl.executeTransfer` llama a `AccountRuleStrategy.validateMovementAllowed` sobre la cuenta origen y la destino antes de mover fondos (p.ej. respeta el día único de movimiento de una cuenta a plazo fijo) |
| Fondos insuficientes en origen | ✅ | Rechazado con 400 antes de tocar cualquiera de las dos cuentas |
| Origen y destino no pueden ser la misma cuenta | ✅ | Validado antes de consultar el repositorio |
| Comisión por exceso de transacciones (Fase 8) | ✅ | Cada lado de la transferencia (`TRANSFER_OUT`/`TRANSFER_IN`) cuenta para el límite mensual gratuito de su propia cuenta, igual que un depósito/retiro |
| Trazabilidad del movimiento | ✅ | Nuevos valores `MovementType.TRANSFER_OUT`/`TRANSFER_IN`, distintos de `DEPOSIT`/`WITHDRAWAL`, para poder identificar en el historial qué movimientos vinieron de una transferencia |

**Nota de diseño:** no hay una operación multi-documento atómica (Mongo
sin transacción de sesión) entre el guardado de la cuenta origen y la
cuenta destino; se aplican de forma secuencial (primero origen, luego
destino), igual que el resto del código existente hace con el movimiento
principal y su comisión (`applyMovementWithPossibleFee`). Aceptable para
el alcance de este proyecto.

## Reportes

> "Generar un reporte completo y general por producto del banco en
> intervalo de tiempo especificado por el usuario."
>
> "Implementar un reporte con los últimos 10 movimientos de la tarjeta de
> débito y de crédito."

| Requisito | Estado | Evidencia |
|---|---|---|
| Reporte por producto en un intervalo de fechas (cuentas) | ✅ | `GET /api/accounts/{id}/movements/report?startDate=&endDate=` |
| Reporte por producto en un intervalo de fechas (créditos) | ✅ | `GET /api/credits/{id}/movements/report?startDate=&endDate=` |
| Últimos 10 movimientos de tarjeta de crédito | ✅ | `GET /api/credits/{id}/movements/last-10`; rechaza con 400 si el crédito no es `CREDIT_CARD` |
| Últimos 10 movimientos de tarjeta de débito | ⚠️ Pendiente (Parte III) | La tarjeta de débito no existe todavía en el modelo de datos: recién se introduce en Parte III de la consigna ("tarjetas de débito asociadas a cuentas... y pagos con ellas"). Implementarla ahora habría significado adelantar, a medias, un producto que la propia consigna define más adelante junto con su funcionalidad de pagos. Cuando se aborde Parte III, agregar el mismo patrón `movements/last-10` sobre el nuevo recurso de tarjeta de débito |

**Nota de interpretación — "reporte por producto":** se interpretó como
el reporte de movimientos de **un producto puntual** (una cuenta o un
crédito por id), no como un reporte agregado de todo el banco por tipo de
producto (p.ej. "todas las cuentas de ahorro"). Esta interpretación es
consistente con el resto de los endpoints de consulta ya existentes en el
sistema (`GET /api/accounts/{id}/movements`, `GET /api/credits/{id}/movements`),
que siempre son por id de producto. Si la intención de la consigna era un
reporte agregado por tipo de producto en todo el banco, sería un endpoint
adicional (`GET /api/accounts/report?accountType=...&startDate=...`) que
agrupe sobre todas las cuentas de ese tipo.

## Tests

`AccountServiceImplTest` (transferencias y reporte) y
`CreditServiceImplTest` (reporte y últimos 10 movimientos de tarjeta)
cubren: caso exitoso, cuenta origen = destino, fondos insuficientes, y
rechazo del reporte de últimos 10 cuando el crédito no es una tarjeta.
