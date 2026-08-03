package com.banco.creditservice.service.strategy;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.BusinessCredit;
import com.banco.creditservice.model.CreditProduct;
import com.banco.creditservice.model.CreditStatus;

import reactor.core.publisher.Mono;

/**
 * Reglas de negocio del credito empresarial: solo para clientes
 * empresariales; a diferencia del personal, una misma empresa puede
 * tener multiples creditos empresariales activos.
 */
@Component
public class BusinessCreditGrantStrategy implements CreditGrantStrategy {

    @Override
    public boolean supports(CreditProduct credit) {
        return credit instanceof BusinessCredit;
    }

    @Override
    public Mono<CreditProduct> prepareAndValidateGrant(CreditProduct credit, CustomerType customerType) {
        if (customerType != CustomerType.BUSINESS) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un credito empresarial solo puede otorgarse a un cliente empresarial"));
        }
        BusinessCredit businessCredit = (BusinessCredit) credit;
        businessCredit.setBalance(businessCredit.getPrincipalAmount());
        return Mono.just(credit);
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
