# Parte I

El sistema a desarrollar está planteado en el contexto del negocio bancario que a medida que se va avanzando en los proyectos, se irá ampliando en base a este mismo proyecto.

## Bases a Desarrollar

- Desarrollo de microservicios con Java 11, 17
- Utilizar Spring Boot como framework base y reactividad con Rx Java.
- El proyecto debe utilizar Maven como manejadores de dependencias.
- Los microservicios proporcionados deben implementar controladores REST.
- Usar el patrón database per service, por lo que un microservicio no puede tocar ninguna tabla o colección que utilice otro microservicio.
- Utilizar inyección de dependencias o por constructor.
- Aplicación de diagramas UML.
- Utilizar propiedades de configuración externalizadas con un Config Server.
- Los nombres de las clases, métodos y las URLs deberán estar en inglés.
- La base de datos a utilizar será MongoDB.
- Uso de Lombok para reducir código.
- Manejo de trazas con Logback y utilizar el nivel del log adecuado.

## Funcionalidades del sistema

- El sistema debe manejar la información de los clientes de un banco.
- Los clientes del banco son de dos tipos: personal o empresarial.
- El sistema debe manejar la información de los siguientes productos que ofrece el banco:
  - Pasivos (cuentas bancarias)
    - Ahorro: libre de comisión por mantenimiento y con un límite máximo de movimientos mensuales.
    - Cuenta corriente: posee comisión de mantenimiento y sin límite de movimientos mensuales.
    - Plazo fijo: libre de comisión por mantenimiento, solo permite un movimiento de retiro o depósito en un día específico del mes.
  - Activos (créditos)
    - Personal: solo se permite un solo crédito por persona.
    - Empresarial: se permite más de un crédito por empresa.
    - Tarjeta de Crédito personal o empresarial.
- Un cliente personal solo puede tener un máximo de una cuenta de ahorro, una cuenta corriente o cuentas a plazo fijo.
- Un cliente empresarial no puede tener una cuenta de ahorro o de plazo fijo pero sí múltiples cuentas corrientes.
- Las cuentas bancarias empresariales pueden tener uno o más titulares y cero o más firmantes autorizados.
- Un cliente puede tener un producto de crédito sin la obligación de tener una cuenta bancaria en la institución.
- Un cliente puede hacer depósitos y retiros de sus cuentas bancarias.
- Un cliente puede hacer pagos de sus productos de crédito.
- Un cliente puede cargar consumos a sus tarjetas de crédito en base a su límite de crédito.
- El sistema debe permitir consultar los saldos disponibles en sus productos como: cuentas bancarias y tarjetas de crédito.
- El sistema debe permitir consultar todos los movimientos de un producto bancario que tiene un cliente.

## Requerimientos no funcionales obligatorios del sistema

- Elaborar y mantener un diagrama en draw.io con el diseño de la solución.
- Elaborar diagramas de secuencia de cada microservicio.
- Elaborar los contratos openapi de los microservicios de la solución.
- El repositorio de datos deberá estar en documentos NoSQL.
- Para el manejo de datos se deberá utilizar Spring Data y no se deberá manejar la creación de SQL dinámicos y evitar el uso de la anotación @Query.
- Para todas las entidades de negocio se debe implementar sus operaciones CRUD: Create, FindAll, Update, Delete.
- Crear los endpoints REST para cada una de las operaciones de los repositorios.
- Utilizar los lineamientos REST para las operaciones CRUD.
- El sistema no tendrá implementado ninguna interfaz gráfica, la verificación de las funcionalidades se realizarán utilizando Postman.

## Recomendaciones y Consideraciones

- Realicen primero las funcionalidades obligatorias.
- Realicen primero las funcionalidades opcionales más sencillas.
- No deben tener configuraciones en el código.
- Las clases y los métodos deben estar comentados.
- El uso de lambdas y streams.
- Deben subir su código a un repositorio git en github.

## Artefactos y entregables

- Cada microservicio deberá tener su propio repositorio.

---

# Parte II

El sistema a desarrollar está planteado en el contexto del negocio bancario que extiende las funcionalidades y requerimientos presentados en el proyecto I. Por lo tanto, en este enunciado solo se agregan nuevas características o modificaciones a las ya presentadas en el proyecto anterior.

## Bases a Desarrollar

Los desarrollos deben continuar con la base de conocimiento requerida en el proyecto anterior, más las que se listan a continuación:

- Desarrollo de las nuevas funcionalidades con programación funcional y reactiva.
- Manejo de colecciones utilizando correctamente las APIs de Streams.
- Agregar el uso de Checkstyle agregando su plugin en el pom.xml.
- Crear un microservicio de registro de APIs con Eureka, habilitar su panel de control e implementar el registro de las APIs para todos los microservicios.
- Crear un microservicio que sirva como API gateway para APIs con Spring Cloud Gateway.
- Implementar circuit breaker en los microservicios usando Resilience4j y configurar un timeout de 2 segundos.
- Implementar patrones de diseño de software.
- Crear test unitarios e implementar Jacoco para visualizar los reportes de cobertura de código de todo el código desarrollado.
- Cada microservicio deberá estar en un contenedor independiente en Docker (deseable).

## Funcionalidades del sistema

Crear APIs implementadas en microservicios que ofrezcan las siguientes funcionalidades:

- Se conservarán las funcionalidades definidas para el proyecto 1.
- Las cuentas bancarias tienen un monto mínimo de apertura que puede ser cero (0).
- El sistema manejará nuevos perfiles de clientes adicionales a los que ya existen, los nuevos perfiles son:

  **Personal:**
  - VIP
    - Cuenta de ahorro que requiere un monto mínimo de promedio diario cada mes. Adicionalmente, para solicitar este producto el cliente debe tener una tarjeta de crédito con el banco al momento de la creación de la cuenta.

  **Empresarial:**
  - PYME
    - Cuenta corriente sin comisión de mantenimiento. Como requisito, el cliente debe tener una tarjeta de crédito con el banco al momento de la creación de la cuenta.

- Todas las cuentas bancarias tendrán un número máximo de transacciones (depósitos y retiros) que no cobrará comisión y superado ese número se cobrará comisión por cada transacción realizada.
- Implementar las transferencias bancarias entre cuentas del mismo cliente y cuentas a terceros del mismo banco.
- El sistema debe generar los siguientes reportes:
  - Generar un reporte completo y general por producto del banco en intervalo de tiempo especificado por el usuario.
  - Implementar un reporte con los últimos 10 movimientos de la tarjeta de débito y de crédito.

---

# Parte III - Proyecto Final

El sistema a desarrollar está planteado en el contexto del negocio bancario que extiende las funcionalidades y requerimientos presentados en el proyecto II. Por lo tanto, en este enunciado solo se agregan nuevas características o modificaciones a las ya presentadas en el proyecto anterior.

## Bases a Desarrollar

Los desarrollos deben continuar con la base de conocimiento requerida en el proyecto anterior, más las que se listan a continuación:

- Desarrollo de las nuevas funcionalidades con programación funcional y reactiva.
- Manejo de colecciones utilizando correctamente las APIs para Streams.
- Los nuevos métodos públicos creados deberán tener sus respectivas pruebas unitarias con los mocks en aquellos casos donde corresponda.
- Presentar el reporte que muestre el coverage respectivo.
- Desarrollo de las nuevas funcionalidades con una arquitectura orientada a eventos usando Kafka como message bróker.
- Los nuevos microservicios no podrán invocar las APIs de los microservicios usando peticiones REST.
- Los controladores que implementen las nuevas funcionalidades deberán ser reactivas usando para ello el modelo de Reactividad usando RX Java y el framework Spring.
- Implementar el flujo de autenticación y autorización utilizando JWT.
- Para el manejo de datos catalogados o maestros se deberá acelerar su acceso utilizando una base de datos de caché con REDIS.
- Elaborar y mantener un diagrama en draw.io con el diseño de la solución.

## Funcionalidades del sistema

- Un cliente no podrá adquirir un producto si posee alguna deuda vencida en algún producto de crédito.
- Un cliente puede hacer el pago de cualquier producto de crédito de terceros.
- Los clientes ahora pueden tener tarjetas de débito asociadas a sus cuentas bancarias y hacer pagos con ellas.
- El banco desea implementar un monedero móvil llamado Yanki con las siguientes características:
  - No se necesita ser cliente del banco para tener un monedero móvil, solo se necesita un número de documento de identificación (DNI, CEX, Pasaporte), número de celular, el IMEI del celular y correo electrónico.
  - El usuario puede recibir y enviar pagos a su monedero con solo su número de celular.
  - Puede asociar su monedero a una tarjeta de débito del banco de manera que el saldo sea cargado o acreditado solo a la cuenta principal asociada a la tarjeta de débito.

## Artefactos y entregables

- Crear y mantener un repositorio en donde tengan los proyectos postman para las pruebas de sus APIs.
- Cada microservicio deberá tener su propio repositorio, la entrega es individual.
