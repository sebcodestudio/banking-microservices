package com.banco.customerservice.service;

import com.banco.customerservice.model.Customer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operaciones CRUD de negocio para clientes (personal / empresarial).
 */
public interface CustomerService {

    /** Registra un nuevo cliente. */
    Mono<Customer> create(Customer customer);

    /** Obtiene todos los clientes registrados. */
    Flux<Customer> findAll();

    /** Busca un cliente por su identificador. */
    Mono<Customer> findById(String id);

    /** Actualiza los datos de un cliente existente. */
    Mono<Customer> update(String id, Customer customer);

    /** Elimina un cliente por su identificador. */
    Mono<Void> delete(String id);
}
