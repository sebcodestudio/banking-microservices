package com.banco.customerservice.service;

import reactor.core.publisher.Mono;

/**
 * Flujo de autenticacion (Fase 13, Parte III): registrar credenciales
 * sobre un cliente existente y emitir un JWT en el login.
 */
public interface AuthService {

    /** Registra o reemplaza las credenciales de un cliente existente. */
    Mono<Void> setCredentials(String customerId, String username, String rawPassword);

    /** Valida usuario/contrasena y, si son correctos, emite un JWT. */
    Mono<TokenResponse> login(String username, String rawPassword);
}
