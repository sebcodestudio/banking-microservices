package com.banco.creditservice.service.strategy;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.CreditProduct;
import com.banco.creditservice.model.CreditStatus;
import com.banco.creditservice.model.PersonalCredit;
import com.banco.creditservice.repository.PersonalCreditRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Reglas de negocio del credito personal: solo para clientes
 * personales, y un unico credito personal activo por cliente.
 */
@Component
@RequiredArgsConstructor
public class PersonalCreditGrantStrategy implements CreditGrantStrategy {

    private final PersonalCreditRepository personalCreditRepository;

    @Override
    public boolean supports(CreditProduct credit) {
        return credit instanceof PersonalCredit;
    }

    @Override
    public Mono<CreditProduct> prepareAndValidateGrant(CreditProduct credit, CustomerType customerType) {
        if (customerType != CustomerType.PERSONAL) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un credito personal solo puede otorgarse a un cliente personal"));
        }
        PersonalCredit personalCredit = (PersonalCredit) credit;
        personalCredit.setBalance(personalCredit.getPrincipalAmount());
        return personalCreditRepository.existsByCustomerIdAndStatus(credit.getCustomerId(), CreditStatus.ACTIVE)
                .flatMap(exists -> exists
                        ? Mono.<CreditProduct>error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "El cliente ya tiene un credito personal activo"))
                        : Mono.just(credit));
    }

    @Override
    public Mono<CreditProduct> validateAndApplyPayment(CreditProduct credit, BigDecimal amount) {
        if (amount.compareTo(credit.getBalance()) > 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pago excede el saldo pendiente del credito"));
        }
        BigDecimal newBalance = credit.getBalance().subtract(amount);
        credit.setBalance(newBalance);
        if (newBalance.signum() == 0) {
            credit.setStatus(CreditStatus.PAID);
        }
        return Mono.just(credit);
    }
}
