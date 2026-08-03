# Colección Postman — Sistema Bancario (Microservicios)

## Archivos

- **banking-microservices.postman_collection.json** — colección única con
  los endpoints de los 3 microservicios de negocio, organizada en carpetas:
  1. Customer Service
  2. Account Service
  3. Credit Service
  4. Fase 8 - Perfiles VIP / PYME y comisiones
  5. Cleanup (ejecutar al final)
- **banking-microservices.postman_environment.json** — environment
  "Banking Microservices - Local" con las URLs base (`customerServiceUrl`,
  `accountServiceUrl`, `creditServiceUrl`, `gatewayUrl`) y las variables
  que se van completando automáticamente durante la ejecución
  (`personalCustomerId`, `savingsAccountId`, `personalCreditId`,
  `creditCardId`, `vipCustomerId`, `pymeCustomerId`, etc.).

## Cómo usar

1. Importar ambos archivos en Postman (`File > Import`).
2. Seleccionar el environment **Banking Microservices - Local**.
3. Levantar los 6 microservicios (`eureka-server` y `config-server`
   primero, luego `customer-service`, `account-service`,
   `credit-service`, y por último `api-gateway`), ya sea de forma local
   (`mvn spring-boot:run` en cada uno) o con `docker compose up --build`
   desde la raíz del proyecto.
4. Ejecutar la colección completa con el **Collection Runner**, carpeta por
   carpeta y en orden (1 → 2 → 3 → 4 → 5), ya que las requests posteriores
   dependen de ids guardados automáticamente por los scripts de test de
   las requests anteriores mediante `pm.environment.set(...)`.

> **Nota (Fase 7):** también se agregó la variable **`gatewayUrl`**
> (`http://localhost:8080`): si se prefiere probar todo a través del API
> Gateway (con su circuit breaker Resilience4j de por medio), basta
> reemplazar `customerServiceUrl`/`accountServiceUrl`/`creditServiceUrl`
> por `{{gatewayUrl}}` en el environment.

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

Cada request incluye un test de Postman (`pm.test`) que valida el código
de estado HTTP esperado (y, en varios casos de Fase 8, también el cuerpo
de la respuesta), de forma que la colección entera puede ejecutarse con
el Collection Runner o con `newman` y reportar éxito/fallo por request.
