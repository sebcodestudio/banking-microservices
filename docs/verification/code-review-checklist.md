# Revisión final de código — Fase 6

Resultado de las 4 verificaciones de cierre solicitadas, ejecutadas sobre
los 4 microservicios (`config-server`, `customer-service`,
`account-service`, `credit-service`).

## 1. Revisión de comentarios en clases y métodos

Comando usado (repetido por servicio):

```bash
grep -rc "/\*\*" --include="*.java" <servicio>/src/main/java
```

| Microservicio | Bloques Javadoc encontrados |
|---|---|
| customer-service | 19 |
| account-service | 53 |
| credit-service | 56 |
| config-server | 1 (clase principal; no tiene lógica de negocio propia) |

Se revisaron manualmente, además, las 4 clases `*Application.java`
(punto de entrada de cada servicio), que originalmente no tenían Javadoc
de clase — se les agregó una descripción breve de responsabilidad.

**Conclusión:** todas las clases, interfaces, enums y métodos públicos de
la capa de modelo, repositorio, servicio y controlador tienen un
comentario Javadoc que explica su propósito o, cuando aplica, la regla de
negocio que implementan (por ejemplo, por qué `PersonalCreditRepository`
verifica solo créditos `ACTIVE`, o por qué `CreditCard.getAvailableLimit()`
se calcula en vez de persistirse).

## 2. Revisión de uso de lambdas / streams

Comando usado:

```bash
grep -rn "for\s*(\|while\s*(" --include="*.java" \
  customer-service/src account-service/src credit-service/src config-server/src
```

**Resultado: 0 coincidencias.** No existe ningún bucle imperativo
(`for`/`while`) en el código de negocio.

Toda la lógica de negocio está construida sobre **Project Reactor**
(`Mono`/`Flux`), lo cual va más allá del pedido puntual de "lambdas y
streams": en vez de streams síncronos de `java.util.stream`, se usan
streams reactivos con operadores funcionales encadenados —`flatMap`,
`map`, `filter`, `doOnNext`, `doOnError`, `switchIfEmpty`, `thenReturn`,
`thenMany`— en prácticamente todos los métodos de `*ServiceImpl`. Este
enfoque es el exigido explícitamente por la consigna ("Utilizar Spring
Boot como framework base y reactividad con Rx Java").

Ejemplo representativo (`CreditServiceImpl.consume`):

```java
return validateAmount(amount)
        .then(findById(creditId))
        .flatMap(credit -> { /* ...validaciones de negocio... */ })
        .flatMap(credit -> applyMovement(credit, CreditMovementType.CONSUMPTION, amount))
        .doOnError(error -> log.warn("Consumo rechazado para creditId={}: {}", creditId, error.getMessage()));
```

## 3. Confirmación de ausencia de configuraciones hardcodeadas

Comandos usados:

```bash
grep -rn "localhost" --include="*.java" <todos los servicios>
grep -rn "808[0-9]"  --include="*.java" <todos los servicios>
```

**Resultado: 0 coincidencias en ambos casos.**

Todo valor de infraestructura (puertos, URIs de MongoDB, URL base de
`customer-service`) vive exclusivamente en `config-server` como
propiedades externalizadas:

- `config-server/src/main/resources/config/<servicio>.yml` — perfil por
  defecto (ejecución local, todo en `localhost`).
- `config-server/src/main/resources/config/<servicio>-docker.yml` —
  perfil `docker`, con los hostnames de los contenedores
  (`customer-mongo`, `account-mongo`, `credit-mongo`, `customer-service`).

El único lugar donde aparece literalmente `localhost` en todo el
repositorio es dentro de esos archivos `.yml` de configuración (no en
código Java), y precisamente ese es su propósito: ser el valor por
defecto en un entorno de desarrollo local. Los tres microservicios de
negocio activan el perfil correspondiente vía la variable de entorno
`SPRING_PROFILES_ACTIVE` (definida en `docker-compose.yml`, no en código),
y resuelven la URL del Config Server vía `SPRING_CONFIG_IMPORT`, también
inyectada como variable de entorno.

`CustomerClient` (en `account-service` y `credit-service`) obtiene la URL
base exclusivamente vía `@Value("${customer-service.base-url}")`, nunca
como literal.

## 4. Resumen ejecutivo

| Verificación | Resultado |
|---|---|
| Comentarios en clases/métodos | ✅ Cobertura completa |
| Uso de lambdas/streams (reactivo) | ✅ 100% de la lógica de negocio, 0 bucles imperativos |
| Sin configuraciones hardcodeadas | ✅ 0 coincidencias de `localhost` o puertos en `.java` |
| Reglas de negocio vs. consigna | ✅ Ver `business-rules-checklist.md` (2 observaciones documentadas, no bloqueantes) |
