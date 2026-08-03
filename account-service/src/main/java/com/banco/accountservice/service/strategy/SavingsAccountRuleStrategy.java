package com.banco.accountservice.service.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CreditClient;
import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.client.CustomerProfile;
import com.banco.accountservice.client.CustomerType;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.repository.AccountMovementRepository;
import com.banco.accountservice.repository.BankAccountRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Reglas de negocio de la cuenta de ahorro: solo para clientes
 * personales, una unica cuenta de ahorro por cliente, y un limite de
 * movimientos mensuales.
 *
 * <p>Fase 8: si el cliente tiene perfil {@link CustomerProfile#VIP},
 * la cuenta exige un monto minimo de promedio diario mensual
 * ({@code minimumDailyAverageBalance}) y que el cliente ya tenga una
 * tarjeta de credito activa con el banco.</p>
 */
@Component
@RequiredArgsConstructor
public class SavingsAccountRuleStrategy implements AccountRuleStrategy {

    private final BankAccountRepository bankAccountRepository;
    private final AccountMovementRepository accountMovementRepository;
    private final CreditClient creditClient;

    @Override
    public boolean supports(BankAccount account) {
        return account instanceof SavingsAccount;
    }

    @Override
    public Mono<BankAccount> prepareAndValidateOpening(BankAccount account, CustomerDto customer) {
        if (customer.customerType() == CustomerType.BUSINESS) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un cliente empresarial no puede abrir una cuenta de ahorro"));
        }
        SavingsAccount savings = (SavingsAccount) account;
        return validateUniqueness(savings)
                .then(validateVipRequirements(savings, customer))
                .thenReturn(account);
    }

    /**
     * Comprueba unicidad sobre {@link BankAccountRepository} (la coleccion
     * "accounts" completa, compartida por los tres subtipos) y filtra en
     * memoria por el tipo concreto: un repositorio tipado a un subtipo
     * (p.ej. {@code SavingsAccountRepository}) no restringe automaticamente
     * sus consultas derivadas por el discriminador {@code _class}, por lo
     * que un {@code existsByHoldersContaining} ahi encontraria tambien
     * cuentas de otro subtipo del mismo cliente.
     */
    private Mono<Void> validateUniqueness(SavingsAccount savings) {
        return bankAccountRepository.findByHoldersContaining(savings.getCustomerId())
                .any(SavingsAccount.class::isInstance)
                .flatMap(exists -> exists
                        ? Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "El cliente ya tiene una cuenta de ahorro"))
                        : Mono.empty());
    }

    /** Aplica los requisitos del perfil VIP; para perfil STANDARD limpia el campo de promedio diario. */
    private Mono<Void> validateVipRequirements(SavingsAccount savings, CustomerDto customer) {
        if (customer.profile() != CustomerProfile.VIP) {
            savings.setMinimumDailyAverageBalance(null);
            return Mono.empty();
        }
        BigDecimal minimumDailyAverageBalance = savings.getMinimumDailyAverageBalance();
        if (minimumDailyAverageBalance == null || minimumDailyAverageBalance.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una cuenta de ahorro VIP requiere un monto minimo de promedio diario mayor a cero"));
        }
        return creditClient.hasActiveCreditCard(savings.getCustomerId())
                .flatMap(hasCreditCard -> hasCreditCard
                        ? Mono.<Void>empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El perfil VIP requiere que el cliente ya tenga una tarjeta de credito con el banco")));
    }

    @Override
    public Mono<Void> validateMovementAllowed(BankAccount account) {
        SavingsAccount savings = (SavingsAccount) account;
        Instant[] monthRange = currentMonthRange();
        return accountMovementRepository
                .findByAccountIdAndMovementDateBetween(savings.getId(), monthRange[0], monthRange[1])
                .count()
                .flatMap(count -> count >= savings.getMonthlyMovementLimit()
                        ? Mono.<Void>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Se alcanzo el limite de movimientos mensuales de la cuenta de ahorro"))
                        : Mono.empty());
    }

    private Instant[] currentMonthRange() {
        LocalDate today = LocalDate.now();
        Instant start = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = today.withDayOfMonth(today.lengthOfMonth())
                .atTime(23, 59, 59)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return new Instant[] { start, end };
    }
}
