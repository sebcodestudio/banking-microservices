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
 * Credito empresarial: prestamo con cuota fija otorgado a un cliente
 * empresarial. A diferencia del credito personal, una misma empresa
 * puede tener multiples creditos empresariales activos simultaneamente.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BusinessCredit extends CreditProduct {

    /** Monto original otorgado; el balance inicial de la deuda es igual a este monto. */
    @NotNull
    @Positive
    private BigDecimal principalAmount;

    /** Tasa de interes anual expresada como fraccion, por ejemplo 0.18 para 18%. */
    @NotNull
    @Positive
    private BigDecimal interestRate;

    @NotNull
    @Positive
    private Integer termMonths;

    @NotNull
    @Positive
    private BigDecimal monthlyPayment;
}
