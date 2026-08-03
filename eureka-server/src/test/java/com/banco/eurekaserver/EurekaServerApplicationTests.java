package com.banco.eurekaserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Prueba de humo: verifica que el contexto de Spring arranca correctamente
 * con la configuracion de Eureka Server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "eureka.client.register-with-eureka=false")
class EurekaServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
