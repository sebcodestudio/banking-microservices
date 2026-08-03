package com.banco.accountservice.service.strategy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CreditClient;
import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.client.CustomerProfile;
import com.banco.accountservice.client.CustomerType;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.CheckingAccount;
import com.banco.accountservice.repository.BankAccountRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Reglas de negocio de la cuenta corriente: un cliente personal solo
 * puede tener una; una empresa puede tener multiples y con firmantes
 * autorizados. No tiene limite de movimientos.
 *
 * <p>Fase 8: si el cliente empresarial tiene perfil
 * {@link CustomerProfile#PYME}, la cuenta queda libre de comision de
 * mantenimiento y exige que el cliente ya tenga una tarjeta de credito
 * activa con el banco.</p>
 */
@Component
@RequiredArgsConstructor
public class CheckingAccountRuleStrategy implements AccountRuleStrategy {

    private final BankAccountRepository bankAccountRepository;
    private final CreditClient creditClient;

    @Override
    public boolean supports(BankAccount account) {
        return account instanceof CheckingAccount;
    }

    @Override
    public Mono<BankAccount> prepareAndValidateOpening(BankAccount account, CustomerDto customer) {
        CheckingAccount checking = (CheckingAccount) account;

        if (customer.customerType() == CustomerType.PERSONAL) {
            checking.setAuthorizedSigners(List.of());
            return validateUniqueness(checking).thenReturn(account);
        }

        return validatePymeRequirements(checking, customer).thenReturn(account);
    }

    /**
     * Comprueba unicidad sobre {@link BankAccountRepository} (la coleccion
     * "accounts" completa, compartida por los tres subtipos) y filtra en
     * memoria por el tipo concreto: un repositorio tipado a un subtipo
     * (p.ej. {@code CheckingAccountRepository}) no restringe automaticamente
     * sus consultas derivadas por el discriminador {@code _class}, por lo
     * que un {@code existsByHoldersContaining} ahi encontraria tambien
     * cuentas de otro subtipo del mismo cliente.
     */
    private Mono<Void> validateUniqueness(CheckingAccount checking) {
        return bankAccountRepository.findByHoldersContaining(checking.getCustomerId())
                .any(CheckingAccount.class::isInstance)
                .flatMap(exists -> exists
                        ? Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "El cliente ya tiene una cuenta corriente"))
                        : Mono.empty());
    }

    /** Aplica los beneficios/requisitos del perfil PYME (solo valido para clientes empresariales). */
    private Mono<Void> validatePymeRequirements(CheckingAccount checking, CustomerDto customer) {
        if (customer.profile() != CustomerProfile.PYME) {
            return Mono.empty();
        }
        checking.setMaintenanceFee(BigDecimal.ZERO);
        return creditClient.hasActiveCreditCard(checking.getCustomerId())
                .flatMap(hasCreditCard -> hasCreditCard
                        ? Mono.<Void>empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El perfil PYME requiere que el cliente ya tenga una tarjeta de credito con el banco")));
    }

    @Override
    public Mono<Void> validateMovementAllowed(BankAccount account) {
        // La cuenta corriente no tiene limite de movimientos.
        return Mono.empty();
    }
}
