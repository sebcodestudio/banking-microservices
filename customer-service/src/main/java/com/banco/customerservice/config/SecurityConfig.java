package com.banco.customerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provee el {@link PasswordEncoder} (BCrypt) usado para hashear
 * contrasenas al registrar credenciales y para verificarlas en el login
 * (Fase 13, Parte III). No se agrega {@code spring-boot-starter-security}
 * completo (con su autoconfiguracion de filtros/HTTP security): solo se
 * usa la libreria de hashing, ya que la validacion del token ocurre en
 * {@code api-gateway}, no aqui.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
