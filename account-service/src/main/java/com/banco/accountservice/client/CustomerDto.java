package com.banco.accountservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vista minima del cliente obtenida desde customer-service, usada para
 * validar reglas de negocio al abrir una cuenta (tipo de cliente y,
 * desde la Fase 8, su perfil comercial VIP/PYME).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerDto(String id, CustomerType customerType, String documentNumber, CustomerProfile profile) {
}
