package com.banco.creditservice.service.strategy;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.CreditProduct;

import reactor.core.publisher.Mono;

/**
 * Patron Strategy: encapsula las reglas de negocio propias de cada
 * subtipo de {@link CreditProduct} (personal, empresarial, tarjeta de
 * credito), reemplazando una cadena de {@code instanceof} en
 * {@code CreditServiceImpl} por una implementacion intercambiable y
 * testeable de forma aislada por tipo de credito.
 *
 * <p>Todas las implementaciones se registran como beans de Spring; el
 * servicio elige la estrategia correcta en tiempo de ejecucion a traves
 * de {@link #supports(CreditProduct)}.</p>
 */
public interface CreditGrantStrategy {

    /** Indica si esta estrategia sabe manejar el tipo concreto del credito dado. */
    boolean supports(CreditProduct credit);

    /**
     * Valida el otorgamiento del credito contra el tipo de cliente y deja
     * el balance inicial correctamente inicializado (deuda para
     * prestamos, cero para tarjetas).
     */
    Mono<CreditProduct> prepareAndValidateGrant(CreditProduct credit, CustomerType customerType);

    /**
     * Valida un pago contra el saldo actual y actualiza el balance (y el
     * estado, si corresponde) del credito. No persiste el cambio: eso lo
     * hace el servicio luego de recibir el credito ya actualizado.
     */
    Mono<CreditProduct> validateAndApplyPayment(CreditProduct credit, BigDecimal amount);

    /**
     * Valida un consumo contra el limite disponible y actualiza el
     * balance del credito. Por defecto se rechaza: solo la tarjeta de
     * credito admite consumos.
     */
    default Mono<CreditProduct> validateAndApplyConsumption(CreditProduct credit, BigDecimal amount) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Solo las tarjetas de credito admiten consumos"));
    }
}
