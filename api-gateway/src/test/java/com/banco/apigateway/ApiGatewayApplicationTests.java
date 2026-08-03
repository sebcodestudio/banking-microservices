package com.banco.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Prueba de humo: verifica que el contexto de Spring arranca sin
 * necesidad de un Config Server o Eureka Server reales. El import
 * obligatorio de configuracion remota ({@code spring.config.import:
 * configserver:...}) queda fuera de esta prueba porque
 * {@code src/test/resources/application.yaml} reemplaza (no fusiona) al
 * {@code application.yaml} de {@code src/main/resources} en el classpath
 * de test; el registro en Eureka se desactiva aparte con
 * {@code eureka.client.enabled=false}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
