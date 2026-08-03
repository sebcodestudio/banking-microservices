package com.banco.customerservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.customerservice.model.Customer;

import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de clientes sobre MongoDB.
 * Se apoya unicamente en los metodos derivados de Spring Data,
 * sin queries dinamicas ni uso de la anotacion @Query.
 */
public interface CustomerRepository extends ReactiveMongoRepository<Customer, String> {

    /** Usado para el login (Fase 13). */
    Mono<Customer> findByUsername(String username);
}
