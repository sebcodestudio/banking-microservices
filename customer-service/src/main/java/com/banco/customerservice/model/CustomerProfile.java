package com.banco.customerservice.model;

/**
 * Perfil comercial del cliente. Determina beneficios y requisitos
 * adicionales al momento de abrir productos bancarios (Fase 8):
 * un cliente {@code VIP} (solo personal) puede abrir una cuenta de
 * ahorro con requisito de promedio diario, y un cliente {@code PYME}
 * (solo empresarial) puede abrir una cuenta corriente sin comision de
 * mantenimiento. Ambos perfiles exigen tener ya una tarjeta de credito
 * con el banco al momento de solicitar esa cuenta.
 */
public enum CustomerProfile {
    /** Perfil por defecto, sin beneficios ni requisitos adicionales. */
    STANDARD,
    /** Solo valido para clientes {@link CustomerType#PERSONAL}. */
    VIP,
    /** Solo valido para clientes {@link CustomerType#BUSINESS}. */
    PYME
}
