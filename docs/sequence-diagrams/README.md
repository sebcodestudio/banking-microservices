# Diagramas de secuencia — Sistema Bancario (Microservicios)

Diagramas de secuencia de los 3 microservicios de negocio, en formato
draw.io (`.drawio`). Se abren directamente en [app.diagrams.net](https://app.diagrams.net)
o en la extensión draw.io de VS Code.

| Archivo | Microservicio | Flujo cubierto |
|---|---|---|
| [customer-service-create-customer.drawio](./customer-service-create-customer.drawio) | customer-service | Alta de un cliente (personal o empresarial) |
| [account-service-open-account-deposit.drawio](./account-service-open-account-deposit.drawio) | account-service | Apertura de cuenta (con validación contra customer-service) y registro de un depósito |
| [credit-service-create-credit-consumption.drawio](./credit-service-create-credit-consumption.drawio) | credit-service | Otorgamiento de un crédito (con validación contra customer-service) y consumo de tarjeta de crédito validado contra el límite disponible |

## Por qué estos flujos

Se eligió, para cada microservicio, el flujo que mejor evidencia:

1. La interacción **REST síncrona con customer-service** (regla no
   funcional: integración entre microservicios vía HTTP, no acceso
   directo a la base de datos de otro servicio).
2. La **regla de negocio distintiva** del microservicio (límites de
   movimientos en cuentas, límite disponible en tarjetas de crédito, único
   crédito personal activo, etc.).
3. La persistencia reactiva contra **su propia base MongoDB** (patrón
   database per service).

> Estos diagramas se mantienen junto al código y deben actualizarse cuando
> cambie el flujo correspondiente (no solo al final del proyecto), igual
> que el diagrama de arquitectura general en `../architecture/architecture.drawio`.
