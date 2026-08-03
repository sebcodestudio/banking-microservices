package com.banco.creditservice.model;

/**
 * Estado del producto de credito dentro del sistema.
 */
public enum CreditStatus {
    /** El credito tiene saldo pendiente (deuda u consumo) y admite operaciones. */
    ACTIVE,
    /** El credito (personal o empresarial) fue cancelado en su totalidad. */
    PAID,
    /** El credito fue cerrado y ya no admite operaciones. */
    CLOSED
}
