package com.banco.customerservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.banco.customerservice.model.Customer;
import com.banco.customerservice.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST con las operaciones CRUD del recurso Customer.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** Crea un nuevo cliente (personal o empresarial). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Customer> create(@Valid @RequestBody Customer customer) {
        return customerService.create(customer);
    }

    /** Obtiene todos los clientes registrados. */
    @GetMapping
    public Flux<Customer> findAll() {
        return customerService.findAll();
    }

    /** Obtiene un cliente por su identificador. */
    @GetMapping("/{id}")
    public Mono<Customer> findById(@PathVariable String id) {
        return customerService.findById(id);
    }

    /** Actualiza un cliente existente identificado por su id. */
    @PutMapping("/{id}")
    public Mono<Customer> update(@PathVariable String id, @Valid @RequestBody Customer customer) {
        return customerService.update(id, customer);
    }

    /** Elimina un cliente identificado por su id. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return customerService.delete(id);
    }
}
