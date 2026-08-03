package com.banco.accountservice.service.strategy;

import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.model.BankAccount;

import reactor.core.publisher.Mono;

/**
 * Patron Strategy: encapsula las reglas de negocio propias de cada
 * subtipo de {@link BankAccount} (ahorro, corriente, plazo fijo),
 * reemplazando una cadena de {@code instanceof} en {@code AccountServiceImpl}
 * por una implementacion intercambiable y testeable de forma aislada por
 * tipo de cuenta.
 *
 * <p>Todas las implementaciones se registran como beans de Spring; el
 * servicio elige la estrategia correcta en tiempo de ejecucion a traves
 * de {@link #supports(BankAccount)}.</p>
 */
public interface AccountRuleStrategy {

    /** Indica si esta estrategia sabe manejar el tipo concreto de la cuenta dada. */
    boolean supports(BankAccount account);

    /**
     * Valida y completa una cuenta nueva antes de guardarla (por ejemplo,
     * rechazar el tipo de cliente incorrecto, verificar unicidad, o
     * aplicar los requisitos de un perfil VIP/PYME segun {@code customer.profile()}).
     * Se asume que los campos comunes (id, holders, openingDate) ya
     * fueron inicializados por el llamador.
     */
    Mono<BankAccount> prepareAndValidateOpening(BankAccount account, CustomerDto customer);

    /**
     * Valida si un nuevo movimiento (deposito o retiro) esta permitido
     * segun las reglas propias del tipo de cuenta (limite mensual,
     * dia especifico del mes, etc.). No valida fondos suficientes ni la
     * comision por exceso de transacciones: ambas son reglas comunes,
     * no especificas del tipo de cuenta.
     */
    Mono<Void> validateMovementAllowed(BankAccount account);
}
