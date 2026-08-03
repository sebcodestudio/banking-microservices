package com.banco.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Punto de entrada de config-server: sirve, en modo nativo, la
 * configuracion externalizada (application.yaml) de customer-service,
 * account-service y credit-service, incluyendo sus variantes por perfil
 * (por ejemplo, "-docker") usadas al ejecutar con docker-compose.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }

}
