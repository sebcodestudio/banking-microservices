package com.banco.creditservice.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Tarjeta de credito, personal o empresarial (el tipo de titular se
 * determina por {@code customerId} contra customer-service, no por un
 * campo propio). El {@code balance} heredado representa el consumo
 * acumulado; el limite disponible se calcula como {@code creditLimit - balance}
 * y nunca se persiste directamente para evitar que quede desincronizado.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CreditCard extends CreditProduct {

    @NotNull
    @Positive
    private BigDecimal creditLimit;

    /** Limite disponible para nuevos consumos, calculado a partir del limite y el consumo actual. */
    public BigDecimal getAvailableLimit() {
        return creditLimit.subtract(getBalance());
    }
}
