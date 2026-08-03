package com.banco.accountservice.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Cuenta corriente: posee comision de mantenimiento y no tiene limite de
 * movimientos mensuales. Un cliente personal puede tener como maximo una;
 * un cliente empresarial puede tener multiples, con uno o mas titulares
 * y cero o mas firmantes autorizados.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CheckingAccount extends BankAccount {

    @NotNull
    private BigDecimal maintenanceFee;
}
