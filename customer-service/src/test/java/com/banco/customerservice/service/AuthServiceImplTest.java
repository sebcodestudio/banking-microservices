package com.banco.customerservice.service;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.banco.customerservice.model.Customer;
import com.banco.customerservice.model.CustomerProfile;
import com.banco.customerservice.model.CustomerType;
import com.banco.customerservice.repository.CustomerRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link AuthServiceImpl} (Fase 13).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(customerRepository, passwordEncoder, jwtService);
    }

    private Customer customer(String id) {
        Customer customer = Customer.builder()
                .customerType(CustomerType.PERSONAL)
                .documentNumber("45678912")
                .email("user@test.com")
                .profile(CustomerProfile.STANDARD)
                .build();
        customer.setId(id);
        return customer;
    }

    @Test
    void setCredentialsHasheaLaContrasenaYLasGuarda() {
        Customer customer = customer("customer-1");
        when(customerRepository.findById("customer-1")).thenReturn(Mono.just(customer));
        when(customerRepository.findByUsername("ana")).thenReturn(Mono.empty());
        when(passwordEncoder.encode("Sup3rSecreta")).thenReturn("hash-ana");
        when(customerRepository.save(customer)).thenReturn(Mono.just(customer));

        StepVerifier.create(authService.setCredentials("customer-1", "ana", "Sup3rSecreta"))
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(customer.getUsername()).isEqualTo("ana");
        org.assertj.core.api.Assertions.assertThat(customer.getPasswordHash()).isEqualTo("hash-ana");
    }

    @Test
    void setCredentialsPropagaNotFoundCuandoElClienteNoExiste() {
        when(customerRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(authService.setCredentials("no-existe", "ana", "Sup3rSecreta"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void setCredentialsRechazaUsernameYaUsadoPorOtroCliente() {
        Customer customer = customer("customer-1");
        Customer other = customer("customer-2");
        other.setUsername("ana");
        when(customerRepository.findById("customer-1")).thenReturn(Mono.just(customer));
        when(customerRepository.findByUsername("ana")).thenReturn(Mono.just(other));

        StepVerifier.create(authService.setCredentials("customer-1", "ana", "Sup3rSecreta"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409)
                .verify();
    }

    @Test
    void loginExitosoEmiteElToken() {
        Customer customer = customer("customer-1");
        customer.setUsername("ana");
        customer.setPasswordHash("hash-ana");
        TokenResponse token = new TokenResponse("jwt-value", "Bearer", 3600);

        when(customerRepository.findByUsername("ana")).thenReturn(Mono.just(customer));
        when(passwordEncoder.matches("Sup3rSecreta", "hash-ana")).thenReturn(true);
        when(jwtService.generateToken(customer)).thenReturn(token);

        StepVerifier.create(authService.login("ana", "Sup3rSecreta"))
                .expectNext(token)
                .verifyComplete();
    }

    @Test
    void loginRechazaContrasenaIncorrecta() {
        Customer customer = customer("customer-1");
        customer.setUsername("ana");
        customer.setPasswordHash("hash-ana");

        when(customerRepository.findByUsername("ana")).thenReturn(Mono.just(customer));
        when(passwordEncoder.matches("incorrecta", "hash-ana")).thenReturn(false);

        StepVerifier.create(authService.login("ana", "incorrecta"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 401)
                .verify();
    }

    @Test
    void loginRechazaUsernameInexistente() {
        when(customerRepository.findByUsername("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(authService.login("no-existe", "cualquiera"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 401)
                .verify();
    }
}
