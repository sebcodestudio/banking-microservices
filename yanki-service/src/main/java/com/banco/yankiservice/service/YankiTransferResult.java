package com.banco.yankiservice.service;

import com.banco.yankiservice.model.YankiTransaction;

/**
 * Resultado de una transferencia entre monederos (Fase 12): el movimiento
 * de envio registrado en el monedero emisor y el de recepcion en el
 * receptor.
 */
public record YankiTransferResult(YankiTransaction sendTransaction, YankiTransaction receiveTransaction) {
}
