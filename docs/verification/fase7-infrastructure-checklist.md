# Fase 7 — Infraestructura transversal: checklist de verificación

Cotejo de los requisitos base de la Fase 7 contra lo implementado.

| Requisito | Estado | Evidencia |
|---|---|---|
| Programación funcional/reactiva para las nuevas features | ✅ | `AccountRuleStrategy`/`CreditGrantStrategy` y su uso en `AccountServiceImpl`/`CreditServiceImpl` son 100% `Mono`/`Flux` con operadores funcionales (`flatMap`, `thenReturn`, `doOnError`); 0 bucles imperativos (verificado con `grep`) |
| Uso correcto de Streams API sobre colecciones | ✅ | `resolveStrategy(...)` usa `List<...>.stream().filter(...).findFirst()` para elegir la estrategia correcta, en vez de un `for`/`switch` |
| Plugin de Checkstyle en el `pom.xml` | ✅ | `checkstyle.xml` compartido (raíz del proyecto, copiado a cada microservicio) + `maven-checkstyle-plugin` en los 6 `pom.xml`, fase `verify`, no bloqueante (`failOnViolation=false`) para adopción gradual |
| Eureka Server + dashboard habilitado | ✅ | `eureka-server` (`@EnableEurekaServer`, puerto 8761); dashboard disponible en `http://localhost:8761` sin configuración adicional |
| Todos los microservicios registrados en Eureka | ✅ | `@EnableDiscoveryClient` en `customer-service`, `account-service`, `credit-service` y `api-gateway`; `eureka.client.service-url.defaultZone` configurado vía `config-server` (local y perfil `docker`) |
| API Gateway con Spring Cloud Gateway | ✅ | `api-gateway` (`spring-cloud-starter-gateway-server-webflux`), 3 rutas (`/api/customers/**`, `/api/accounts/**`, `/api/credits/**`) resueltas vía `lb://<servicio>` (balanceo de carga + descubrimiento vía Eureka) |
| Circuit breaker con Resilience4j | ✅ | Aplicado en 2 niveles: (1) por ruta en `api-gateway` (`GatewayConfig`, filtro `CircuitBreaker` + `FallbackController`); (2) en la llamada interna `account-service`/`credit-service` → `customer-service` (`CustomerClient`, `ReactiveCircuitBreakerFactory`) |
| Timeout de 2 segundos | ✅ | `TimeLimiterConfig.timeoutDuration(Duration.ofSeconds(2))` en `api-gateway` (`GatewayConfig`) y `resilience4j.timelimiter.instances.customerServiceCB.timeoutDuration: 2s` en la configuración externalizada (`config-server`) de `account-service`/`credit-service` |
| Patrones de diseño | ✅ | **Strategy** aplicado dos veces: `AccountRuleStrategy` (reglas de apertura/movimientos por tipo de cuenta) y `CreditGrantStrategy` (reglas de otorgamiento/pago/consumo por tipo de crédito), reemplazando las cadenas `instanceof` previas. También se usa **Builder** (Lombok `@Builder`/`@SuperBuilder`) y **Repository** (Spring Data) en todo el proyecto desde la Parte I |
| Pruebas unitarias | ✅ | JUnit 5 + Mockito + `reactor-test` (`StepVerifier`): `CustomerServiceImplTest`, `AccountServiceImplTest` + 3 tests de estrategia, `CreditServiceImplTest` + 2 tests de estrategia, además de los tests de humo y del `FallbackController` en `eureka-server`/`api-gateway` |
| Reporte de cobertura (Jacoco) | ✅ | `jacoco-maven-plugin` en los 6 `pom.xml`; reporte HTML en `target/site/jacoco/index.html` tras `mvn test` |
| Cada microservicio en su propio contenedor Docker | ✅ | `Dockerfile` multi-stage (Maven+JDK17 → JRE17) en los 6 microservicios; `docker-compose.yml` actualizado con `eureka-server` y `api-gateway`, con `depends_on`/`healthcheck` para respetar el orden de arranque |

## Decisiones de diseño relevantes

- **`eureka-server` no depende de `config-server`.** Se mantiene
  autocontenido (su propio `application.yaml` + `application-docker.yaml`)
  para evitar un problema de arranque circular: los demás
  microservicios necesitan `config-server` para saber la URL de Eureka,
  así que Eureka mismo no puede depender de `config-server` para existir.
- **`account-service`/`credit-service` migraron `customer-service.base-url`
  de una URL fija a `lb://customer-service`.** Esto hace que la
  resolución de la instancia real pase siempre por Eureka (con balanceo
  de carga si en el futuro hay múltiples instancias de
  `customer-service`), en vez de asumir un único host fijo.
- **El circuit breaker se aplicó en dos capas (gateway + llamada
  interna) y no solo en una.** El del gateway protege al cliente externo
  (Postman) de una ruta caída; el de `CustomerClient` protege la lógica
  de negocio interna de `account-service`/`credit-service` de que
  `customer-service` esté lento o caído, incluso si alguien llama a esos
  microservicios directamente sin pasar por el gateway.
- **Checkstyle y Jacoco se dejaron en modo no bloqueante** para el
  build normal (`mvn package`), de forma que el `docker-compose up
  --build` no se rompa por advertencias de estilo; se ejecutan al correr
  explícitamente `mvn verify` o `mvn test`.
