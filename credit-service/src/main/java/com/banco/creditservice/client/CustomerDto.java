package com.banco.creditservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vista minima del cliente obtenida desde customer-service, usada solo
 * para validar reglas de negocio al otorgar un credito.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerDto(String id, CustomerType customerType, String documentNumber) {
}
