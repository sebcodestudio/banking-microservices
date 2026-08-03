package com.banco.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Punto de entrada de eureka-server: servidor de registro y descubrimiento
 * de servicios (Netflix Eureka) para todos los microservicios del sistema
 * bancario. Expone el panel de control en la raiz ("/") y el endpoint de
 * registro/renovacion que usan los clientes Eureka de cada microservicio.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }

}
