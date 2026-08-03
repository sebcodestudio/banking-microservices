# Revisión cruzada — Reglas de negocio vs. consigna original

Cotejo punto por punto de `nota.md` (consigna original) contra la
implementación en `customer-service`, `account-service` y `credit-service`.

## Bases técnicas obligatorias

| Requisito | Estado | Evidencia |
|---|---|---|
| Java 11/17 | ✅ | `java.version=17` en los 4 `pom.xml` |
| Spring Boot + reactividad (Rx) | ✅ | WebFlux + Project Reactor (`Mono`/`Flux`) en los 3 microservicios de negocio |
| Maven | ✅ | Un `pom.xml` independiente por microservicio |
| Controladores REST | ✅ | `CustomerController`, `AccountController`, `CreditController` |
| Database per service | ✅ | `customer_db`, `account_db`, `credit_db`; en Docker, además, 3 contenedores Mongo separados (`customer-mongo`, `account-mongo`, `credit-mongo`) |
| Inyección por constructor | ✅ | `@RequiredArgsConstructor` (Lombok) en todos los `*ServiceImpl` y controladores; nunca `@Autowired` sobre campo |
| Diagramas UML | ✅ | Diagramas de secuencia (`docs/sequence-diagrams/`) + diagrama de arquitectura (`docs/architecture/architecture.drawio`) |
| Config Server externalizado | ✅ | `config-server` (native profile) sirve `application.yaml` de cada servicio, incluyendo variantes `-docker` |
| Nombres en inglés | ✅ | Clases, métodos y rutas (`/api/customers`, `/api/accounts`, `/api/credits`, etc.) en inglés; el texto de negocio (mensajes de error, comentarios) está en español, lo cual no infringe el requisito porque este habla de "nombres" de clases/métodos/URLs |
| MongoDB | ✅ | `spring-boot-starter-data-mongodb-reactive` en los 3 servicios de negocio |
| Lombok | ✅ | `@Getter/@Setter/@Builder/@SuperBuilder/@RequiredArgsConstructor/@Slf4j` en los 4 microservicios |
| Logback + nivel de log adecuado | ✅ | `logback-spring.xml` por servicio (`com.banco`→DEBUG, `org.springframework.data.mongodb`→WARN, `root`→INFO); `log.info` en altas/movimientos exitosos, `log.warn` en reglas de negocio rechazadas |

## Funcionalidades del sistema

| Requisito | Estado | Evidencia |
|---|---|---|
| Clientes personal / empresarial | ✅ | `Customer` con `customerType` discriminado (`PersonalCustomer`/`BusinessCustomer` vía herencia) |
| Cuenta de ahorro: sin comisión, límite de movimientos mensuales | ✅ | `SavingsAccount.monthlyMovementLimit`, validado en `validateMovementAllowed` |
| Cuenta corriente: con comisión, sin límite de movimientos | ✅ | `CheckingAccount.maintenanceFee`, sin restricción de conteo |
| Cuenta a plazo fijo: sin comisión, un movimiento en un día específico del mes | ✅ | `FixedTermAccount.specificMovementDay`, validado contra `LocalDate.now().getDayOfMonth()` y contra movimientos ya existentes en el período |
| Crédito personal: uno solo por persona | ✅ | `PersonalCreditRepository.existsByCustomerIdAndStatus(customerId, ACTIVE)`; se documenta en el código que la interpretación es "un crédito personal **activo** a la vez" (ver nota abajo) |
| Crédito empresarial: más de uno por empresa | ✅ | `BusinessCreditRepository` sin restricción de unicidad |
| Tarjeta de crédito personal o empresarial | ✅ | `CreditCard` no exige `customerType` específico; se otorga a cualquier cliente válido |
| Cliente personal: máx. 1 ahorro, 1 corriente o cuentas a plazo fijo | ⚠️ Parcial — ver nota | `existsByHoldersContaining` limita a 1 cuenta de ahorro y 1 corriente por cliente personal; **no se limita expresamente la cantidad de cuentas a plazo fijo**, ya que la consigna solo dice "una cuenta de ahorro, una cuenta corriente **o** cuentas a plazo fijo" (plural), lo que se interpretó como plazo fijo sin tope. Si el equipo docente exige también un único plazo fijo, ajustar `validateAndPrepare` en `AccountServiceImpl` añadiendo la misma verificación `existsByHoldersContaining` para `FixedTermAccount` |
| Cliente empresarial: sin ahorro/plazo fijo, sí múltiples corrientes | ✅ | `validateAndPrepare` rechaza `SavingsAccount`/`FixedTermAccount` con `customerType == BUSINESS`; `CheckingAccount` no tiene restricción de unicidad para empresas |
| Cuentas empresariales: titulares y firmantes autorizados | ✅ | `BankAccount.holders` (≥1) y `CheckingAccount.authorizedSigners` (0..N) |
| Crédito sin necesidad de cuenta bancaria | ✅ | `credit-service` solo valida la existencia del cliente en `customer-service`, nunca contra `account-service` |
| Depósitos y retiros | ✅ | `POST /api/accounts/{id}/deposits`, `POST /api/accounts/{id}/withdrawals` |
| Pagos de productos de crédito | ✅ | `POST /api/credits/{id}/payments` |
| Consumos de tarjeta según límite | ✅ | `POST /api/credits/{id}/consumptions`, validado contra `getAvailableLimit()` |
| Consulta de saldos (cuentas y tarjetas) | ✅ | `GET /api/accounts/{id}/balance`, `GET /api/credits/{id}/balance` |
| Consulta de movimientos | ✅ | `GET /api/accounts/{id}/movements`, `GET /api/credits/{id}/movements` |

## Requerimientos no funcionales obligatorios

| Requisito | Estado | Evidencia |
|---|---|---|
| Diagrama draw.io mantenido, no solo al final | ✅ | `docs/architecture/architecture.drawio`; se recomienda actualizarlo en cada PR que cambie la topología |
| Diagramas de secuencia por microservicio | ✅ | 1 diagrama por cada uno de los 3 microservicios de negocio en `docs/sequence-diagrams/` |
| Contratos OpenAPI | ✅ | `customer-service.yaml`, `account-service.yaml`, `credit-service.yaml`, consolidados en `docs/openapi/` |
| Repositorio NoSQL | ✅ | MongoDB |
| Spring Data sin SQL dinámico ni `@Query` | ✅ | Todos los repositorios usan únicamente métodos derivados (`existsBy...`, `findBy...`); se verificó con `grep -r "@Query"` → sin resultados |
| CRUD completo por entidad de negocio | ✅ | `Customer`, `BankAccount` (3 subtipos), `CreditProduct` (3 subtipos) tienen `create/findAll/findById/update/delete` |
| Endpoints REST por operación de repositorio | ✅ | Un endpoint por cada operación de negocio expuesta |
| Lineamientos REST | ✅ | Verbos y códigos de estado correctos: `POST`→201, `GET`→200, `PUT`→200, `DELETE`→204, errores de negocio→400/404/409 |
| Sin interfaz gráfica, verificación por Postman | ✅ | `docs/postman/banking-microservices.postman_collection.json` + environment |

## Recomendaciones

| Requisito | Estado | Evidencia |
|---|---|---|
| Sin configuraciones en el código | ✅ | Verificado con `grep -rn "localhost"` y `grep -rn "808[0-9]"` sobre todo el código `.java` → sin resultados; todo vive en `config-server` (con perfil `docker` para contenedores) |
| Clases y métodos comentados | ✅ | Javadoc en clases, interfaces y métodos públicos de los 4 microservicios (ver `docs/verification/code-review-checklist.md`) |
| Uso de lambdas y streams | ✅ | Toda la capa de negocio es reactiva (`Mono`/`Flux` con `flatMap`, `map`, `doOnNext`, `doOnError`, `switchIfEmpty`); no hay ningún `for`/`while` imperativo en el código de negocio (verificado con grep) |
| Repositorio Git en GitHub | ⚠️ Fuera de alcance de esta entrega | No aplica al entregable en zip; el proyecto ya está organizado por carpeta/microservicio, lista para subir a un repo (o a 4 repos, ver siguiente punto) |

## Artefactos y entregables

| Requisito | Estado | Nota |
|---|---|---|
| Cada microservicio con su propio repositorio | ⚠️ Pendiente de decisión del equipo | En este entregable los 4 microservicios viven en una sola carpeta raíz para facilitar la revisión y el `docker-compose.yml` conjunto. Para cumplir el requisito al pie de la letra, cada carpeta (`config-server/`, `customer-service/`, `account-service/`, `credit-service/`) puede promoverse a su propio repositorio Git independiente sin cambios de código, ya que no comparten dependencias Maven entre sí (no hay un `pom.xml` padre agregador) |

## Nota sobre la interpretación de "un solo crédito por persona"

La consigna dice literalmente: *"Personal: solo se permite un solo crédito
por persona"*. Se interpretó como **un crédito personal activo a la vez**
(`existsByCustomerIdAndStatus(customerId, ACTIVE)`), y no como un tope de
uno en toda la vida del cliente. Esta decisión de diseño se documentó en
el Javadoc de `PersonalCreditRepository` y en `CreditServiceImpl`, de modo
que un cliente que ya canceló su crédito personal (`status = PAID`) sí
puede solicitar uno nuevo. Si la intención de la consigna era más
restrictiva, basta cambiar el método a `existsByCustomerId(customerId)` en
`PersonalCreditRepository` y ajustar la llamada en `CreditServiceImpl.validateAndPrepare`.
