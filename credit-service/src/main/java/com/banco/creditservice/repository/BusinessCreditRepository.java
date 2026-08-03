package com.banco.creditservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.creditservice.model.BusinessCredit;

/**
 * Repositorio reactivo de creditos empresariales. Spring Data filtra
 * automaticamente por el discriminador de tipo (_class) de esta subclase.
 * No se define ninguna restriccion de unicidad: una empresa puede tener
 * multiples creditos empresariales activos.
 */
public interface BusinessCreditRepository extends ReactiveMongoRepository<BusinessCredit, String> {
}
