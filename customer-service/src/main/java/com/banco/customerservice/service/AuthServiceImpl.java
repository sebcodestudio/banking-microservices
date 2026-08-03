package com.banco.customerservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.banco.customerservice.model.Customer;
import com.banco.customerservice.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Implementacion del flujo de autenticacion (Fase 13).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public Mono<Void> setCredentials(String customerId, String username, String rawPassword) {
        return customerRepository.findById(customerId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found with id " + customerId)))
                .flatMap(customer -> validateUsernameAvailable(customerId, username).thenReturn(customer))
                .flatMap(customer -> {
                    customer.setUsername(username);
                    customer.setPasswordHash(passwordEncoder.encode(rawPassword));
                    return customerRepository.save(customer);
                })
                .doOnNext(saved -> log.info("Credenciales registradas para customerId={}", saved.getId()))
                .then();
    }

    private Mono<Void> validateUsernameAvailable(String customerId, String username) {
        return customerRepository.findByUsername(username)
                .flatMap(existing -> existing.getId().equals(customerId)
                        ? Mono.<Customer>empty()
                        : Mono.<Customer>error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "El nombre de usuario ya esta en uso")))
                .then();
    }

    @Override
    public Mono<TokenResponse> login(String username, String rawPassword) {
        return customerRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(unauthorized()))
                .flatMap(customer -> passwordEncoder.matches(rawPassword, customer.getPasswordHash())
                        ? Mono.just(customer)
                        : Mono.<Customer>error(unauthorized()))
                .map(jwtService::generateToken)
                .doOnError(error -> log.warn("Login rechazado para username={}: {}", username, error.getMessage()));
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contrasena invalidos");
    }
}
