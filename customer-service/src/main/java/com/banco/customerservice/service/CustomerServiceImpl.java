package com.banco.customerservice.service;

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.banco.customerservice.model.Customer;
import com.banco.customerservice.model.CustomerProfile;
import com.banco.customerservice.model.CustomerType;
import com.banco.customerservice.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementacion de las operaciones CRUD de clientes usando el repositorio reactivo.
 *
 * <p>Fase 14 (Parte III): {@link #findById(String)} -el dato "catalogado"
 * que account-service/credit-service consultan con mas frecuencia via
 * {@code CustomerClient}- pasa primero por cache Redis
 * ({@code ReactiveRedisTemplate}, no {@code @Cacheable}: esa anotacion no
 * es fiable con tipos reactivos porque cachearia el {@code Mono} sin
 * resolver en vez del valor). La entrada se invalida en {@link #update}
 * y {@link #delete}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final CustomerRepository customerRepository;
    private final ReactiveRedisTemplate<String, Customer> customerRedisTemplate;

    @Override
    public Mono<Customer> create(Customer customer) {
        customer.setId(null);
        return validateProfile(customer)
                .then(customerRepository.save(customer))
                .doOnNext(saved -> log.info("Cliente creado: id={}, type={}, profile={}",
                        saved.getId(), saved.getCustomerType(), saved.getProfile()))
                .doOnError(error -> log.warn("Alta de cliente rechazada: {}", error.getMessage()));
    }

    @Override
    public Flux<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    public Mono<Customer> findById(String id) {
        String cacheKey = cacheKey(id);
        return customerRedisTemplate.opsForValue().get(cacheKey)
                .switchIfEmpty(customerRepository.findById(id)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Customer not found with id " + id)))
                        .flatMap(customer -> customerRedisTemplate.opsForValue()
                                .set(cacheKey, customer, CACHE_TTL)
                                .thenReturn(customer)))
                .doOnError(error -> log.warn("Cliente no encontrado: id={}", id));
    }

    @Override
    public Mono<Customer> update(String id, Customer customer) {
        return customerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found with id " + id)))
                .flatMap(existing -> validateProfile(customer).thenReturn(existing))
                .flatMap(existing -> {
                    customer.setId(existing.getId());
                    customer.setCreatedAt(existing.getCreatedAt());
                    return customerRepository.save(customer);
                })
                .flatMap(updated -> evictCache(id).thenReturn(updated))
                .doOnNext(updated -> log.info("Cliente actualizado: id={}", updated.getId()));
    }

    @Override
    public Mono<Void> delete(String id) {
        return customerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found with id " + id)))
                .flatMap(customerRepository::delete)
                .then(evictCache(id))
                .doOnSuccess(v -> log.info("Cliente eliminado: id={}", id));
    }

    private Mono<Void> evictCache(String id) {
        return customerRedisTemplate.opsForValue().delete(cacheKey(id)).then();
    }

    private String cacheKey(String id) {
        return "customer:" + id;
    }

    /**
     * Valida que el perfil comercial (Fase 8) sea compatible con el tipo
     * de cliente: {@code VIP} solo para clientes personales, {@code PYME}
     * solo para clientes empresariales.
     */
    private Mono<Void> validateProfile(Customer customer) {
        CustomerProfile profile = customer.getProfile();
        if (profile == CustomerProfile.VIP && customer.getCustomerType() != CustomerType.PERSONAL) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El perfil VIP solo es valido para clientes personales"));
        }
        if (profile == CustomerProfile.PYME && customer.getCustomerType() != CustomerType.BUSINESS) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El perfil PYME solo es valido para clientes empresariales"));
        }
        return Mono.empty();
    }
}
