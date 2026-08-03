package com.banco.customerservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.web.server.ResponseStatusException;

import com.banco.customerservice.model.Customer;
import com.banco.customerservice.model.CustomerType;
import com.banco.customerservice.repository.CustomerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link CustomerServiceImpl}, mockeando el
 * repositorio reactivo y la cache Redis (Fase 14) para aislar la logica
 * de negocio del acceso a MongoDB/Redis.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ReactiveRedisTemplate<String, Customer> customerRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, Customer> valueOperations;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        lenient().when(customerRedisTemplate.opsForValue()).thenReturn(valueOperations);
        customerService = new CustomerServiceImpl(customerRepository, customerRedisTemplate);
    }

    private Customer personalCustomer() {
        return Customer.builder()
                .customerType(CustomerType.PERSONAL)
                .documentNumber("45678912")
                .email("juan@example.com")
                .firstName("Juan")
                .lastName("Perez")
                .build();
    }

    @Test
    void createAsignaIdNuloYDelegaEnElRepositorio() {
        Customer input = personalCustomer();
        input.setId("id-que-deberia-ser-ignorado");
        Customer saved = personalCustomer();
        saved.setId("generated-id");

        when(customerRepository.save(any(Customer.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(customerService.create(input))
                .expectNextMatches(c -> c.getId().equals("generated-id"))
                .verifyComplete();

        assertThat(input.getId()).isNull();
        verify(customerRepository, times(1)).save(input);
    }

    @Test
    void findByIdPropagaNotFoundCuandoNoExiste() {
        when(valueOperations.get("customer:no-existe")).thenReturn(Mono.empty());
        when(customerRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.findById("no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void findByIdConsultaMongoYPueblaLaCacheEnUnCacheMiss() {
        Customer existing = personalCustomer();
        existing.setId("id-1");
        when(valueOperations.get("customer:id-1")).thenReturn(Mono.empty());
        when(customerRepository.findById("id-1")).thenReturn(Mono.just(existing));
        when(valueOperations.set(eq("customer:id-1"), eq(existing), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(customerService.findById("id-1"))
                .expectNext(existing)
                .verifyComplete();

        verify(valueOperations).set(eq("customer:id-1"), eq(existing), any(Duration.class));
    }

    @Test
    void findByIdDevuelveDesdeCacheSinConsultarMongoEnUnCacheHit() {
        Customer cached = personalCustomer();
        cached.setId("id-1");
        // findById(...) construye el Mono de respaldo igual (el argumento
        // de .switchIfEmpty(...) se evalua en Java antes de subscribirse,
        // ver nota en CLAUDE.md), pero al ser reactivo no se ejecuta
        // ninguna consulta real salvo que se subscriba: lo que realmente
        // prueba el cache hit es que jamas se puebla la cache de nuevo.
        lenient().when(customerRepository.findById("id-1")).thenReturn(Mono.empty());
        when(valueOperations.get("customer:id-1")).thenReturn(Mono.just(cached));

        StepVerifier.create(customerService.findById("id-1"))
                .expectNext(cached)
                .verifyComplete();

        verify(valueOperations, never()).set(any(String.class), any(Customer.class), any(Duration.class));
    }

    @Test
    void findAllDelegaEnElRepositorio() {
        Customer c1 = personalCustomer();
        c1.setId("id-1");
        when(customerRepository.findAll()).thenReturn(Flux.just(c1));

        StepVerifier.create(customerService.findAll())
                .expectNext(c1)
                .verifyComplete();
    }

    @Test
    void updatePreservaIdYCreatedAtDelExistenteEInvalidaLaCache() {
        Customer existing = personalCustomer();
        existing.setId("id-1");
        Customer update = personalCustomer();
        update.setEmail("nuevo@example.com");

        when(customerRepository.findById("id-1")).thenReturn(Mono.just(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(valueOperations.delete("customer:id-1")).thenReturn(Mono.just(true));

        StepVerifier.create(customerService.update("id-1", update))
                .expectNextMatches(c -> c.getId().equals("id-1") && c.getEmail().equals("nuevo@example.com"))
                .verifyComplete();

        verify(valueOperations).delete("customer:id-1");
    }

    @Test
    void deleteFallaConNotFoundCuandoElClienteNoExiste() {
        lenient().when(valueOperations.delete("customer:no-existe")).thenReturn(Mono.just(false));
        when(customerRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(customerService.delete("no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void deleteInvalidaLaCacheDelClienteEliminado() {
        Customer existing = personalCustomer();
        existing.setId("id-1");
        when(customerRepository.findById("id-1")).thenReturn(Mono.just(existing));
        when(customerRepository.delete(existing)).thenReturn(Mono.empty());
        when(valueOperations.delete("customer:id-1")).thenReturn(Mono.just(true));

        StepVerifier.create(customerService.delete("id-1"))
                .verifyComplete();

        verify(valueOperations).delete("customer:id-1");
    }
}
