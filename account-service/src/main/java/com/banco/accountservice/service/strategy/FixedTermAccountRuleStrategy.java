package com.banco.accountservice.service.strategy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.client.CustomerType;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.FixedTermAccount;
import com.banco.accountservice.repository.AccountMovementRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Reglas de negocio de la cuenta a plazo fijo: solo para clientes
 * personales, y un unico movimiento permitido en un dia especifico de
 * cada mes.
 */
@Component
@RequiredArgsConstructor
public class FixedTermAccountRuleStrategy implements AccountRuleStrategy {

    private final AccountMovementRepository accountMovementRepository;

    @Override
    public boolean supports(BankAccount account) {
        return account instanceof FixedTermAccount;
    }

    @Override
    public Mono<BankAccount> prepareAndValidateOpening(BankAccount account, CustomerDto customer) {
        if (customer.customerType() == CustomerType.BUSINESS) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un cliente empresarial no puede abrir una cuenta a plazo fijo"));
        }
        return Mono.just(account);
    }

    @Override
    public Mono<Void> validateMovementAllowed(BankAccount account) {
        FixedTermAccount fixedTerm = (FixedTermAccount) account;
        int today = LocalDate.now().getDayOfMonth();
        if (today != fixedTerm.getSpecificMovementDay()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las cuentas a plazo fijo solo permiten movimientos el dia "
                            + fixedTerm.getSpecificMovementDay() + " del mes"));
        }
        Instant[] monthRange = currentMonthRange();
        return accountMovementRepository
                .findByAccountIdAndMovementDateBetween(fixedTerm.getId(), monthRange[0], monthRange[1])
                .hasElements()
                .flatMap(hasMovement -> hasMovement
                        ? Mono.<Void>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "La cuenta a plazo fijo ya tuvo su unico movimiento de este periodo"))
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
