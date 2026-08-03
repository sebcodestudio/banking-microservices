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
 * Credito personal: prestamo con cuota fija otorgado a un cliente
 * personal. Regla de negocio: un cliente personal solo puede tener un
 * unico credito personal activo (validado en el service).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PersonalCredit extends CreditProduct {

    /** Monto original otorgado; el balance inicial de la deuda es igual a este monto. */
    @NotNull
    @Positive
    private BigDecimal principalAmount;

    /** Tasa de interes anual expresada como fraccion, por ejemplo 0.15 para 15%. */
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
