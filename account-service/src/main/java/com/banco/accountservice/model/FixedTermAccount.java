package com.banco.accountservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Cuenta a plazo fijo: libre de comision de mantenimiento, solo permite un
 * movimiento (retiro o deposito) en un dia especifico del mes. Un cliente
 * personal puede tener una o mas; los clientes empresariales no pueden
 * abrir este tipo de cuenta.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FixedTermAccount extends BankAccount {

    /** Dia del mes (1-31) en que se permite el unico movimiento del periodo. */
    @NotNull
    @Min(1)
    @Max(31)
    private Integer specificMovementDay;

    @NotNull
    @Positive
    private Integer termMonths;
}
