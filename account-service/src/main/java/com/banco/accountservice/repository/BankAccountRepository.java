package com.banco.accountservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.accountservice.model.BankAccount;

import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo general de cuentas bancarias (sobre la coleccion
 * "accounts", compartida por los tres tipos de cuenta).
 */
public interface BankAccountRepository extends ReactiveMongoRepository<BankAccount, String> {

    /** Todas las cuentas donde el cliente aparece como titular. */
    Flux<BankAccount> findByHoldersContaining(String customerId);
}
