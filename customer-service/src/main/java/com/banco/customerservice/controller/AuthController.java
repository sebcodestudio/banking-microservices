package com.banco.customerservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.banco.customerservice.service.AuthService;
import com.banco.customerservice.service.TokenResponse;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Controlador REST del flujo de autenticacion (Fase 13, Parte III).
 * Expuesto con RxJava ({@link Single}/{@link Completable}) como el resto
 * de la funcionalidad nueva; internamente delega en {@link AuthService},
 * que trabaja en Reactor. {@code api-gateway} valida el JWT emitido aqui
 * en las rutas protegidas; este servicio solo lo emite.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Autentica con usuario/contrasena y emite un JWT. */
    @PostMapping("/api/auth/login")
    public Single<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return RxJava3Adapter.monoToSingle(authService.login(request.username(), request.password()));
    }

    /** Registra o reemplaza las credenciales de un cliente existente para que pueda loguearse. */
    @PostMapping("/api/customers/{id}/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable setCredentials(@PathVariable String id, @Valid @RequestBody SetCredentialsRequest request) {
        return RxJava3Adapter.monoToCompletable(
                authService.setCredentials(id, request.username(), request.password()));
    }
}
