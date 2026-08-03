package com.banco.accountservice.model;

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
 * Cuenta de ahorro: libre de comision de mantenimiento, con un limite
 * maximo de movimientos mensuales. Un cliente personal puede tener como
 * maximo una; los clientes empresariales no pueden abrir este tipo de cuenta.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SavingsAccount extends BankAccount {

    /** Cantidad maxima de movimientos (depositos + retiros) permitidos por mes. */
    @NotNull
    @Positive
    private Integer monthlyMovementLimit;

    /**
     * Monto minimo de promedio diario mensual (Fase 8). Solo aplica, y es
     * obligatorio, cuando el cliente titular tiene perfil {@code VIP};
     * para clientes con perfil {@code STANDARD} se ignora y queda en
     * {@code null}.
     */
    private BigDecimal minimumDailyAverageBalance;
}
