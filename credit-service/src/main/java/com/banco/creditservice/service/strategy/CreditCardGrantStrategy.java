package com.banco.creditservice.service.strategy;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.CreditCard;
import com.banco.creditservice.model.CreditProduct;

import reactor.core.publisher.Mono;

/**
 * Reglas de negocio de la tarjeta de credito: puede otorgarse a un
 * cliente personal o empresarial (no se restringe por customerType), y
 * es el unico tipo de credito que admite consumos, validados contra el
 * limite disponible.
 */
@Component
public class CreditCardGrantStrategy implements CreditGrantStrategy {

    @Override
    public boolean supports(CreditProduct credit) {
        return credit instanceof CreditCard;
    }

    @Override
    public Mono<CreditProduct> prepareAndValidateGrant(CreditProduct credit, CustomerType customerType) {
        // No se restringe por tipo de cliente: la tarjeta puede ser personal o empresarial.
        credit.setBalance(BigDecimal.ZERO);
        return Mono.just(credit);
    }

    @Override
    public Mono<CreditProduct> validateAndApplyPayment(CreditProduct credit, BigDecimal amount) {
        if (amount.compareTo(credit.getBalance()) > 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pago excede el saldo pendiente del credito"));
        }
        // La tarjeta nunca pasa a PAID: consumo acumulado, no una deuda con fin definido.
        credit.setBalance(credit.getBalance().subtract(amount));
        return Mono.just(credit);
    }

    @Override
    public Mono<CreditProduct> validateAndApplyConsumption(CreditProduct credit, BigDecimal amount) {
        CreditCard creditCard = (CreditCard) credit;
        if (amount.compareTo(creditCard.getAvailableLimit()) > 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El consumo excede el limite disponible de la tarjeta"));
        }
        creditCard.setBalance(creditCard.getBalance().add(amount));
        return Mono.just(credit);
    }
}
