package com.banco.accountservice.controller;

import com.banco.accountservice.model.AccountMovement;

/**
 * Resultado de una transferencia (Fase 9): el movimiento de salida
 * registrado en la cuenta origen y el de entrada registrado en la cuenta
 * destino.
 */
public record TransferResult(AccountMovement sourceMovement, AccountMovement destinationMovement) {
}
