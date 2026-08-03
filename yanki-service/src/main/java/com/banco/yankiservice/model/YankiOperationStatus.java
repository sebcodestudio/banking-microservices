package com.banco.yankiservice.model;

/**
 * Estado de una {@link YankiOperation} asincrona via Kafka (Fase 12).
 */
public enum YankiOperationStatus {
    PENDING,
    COMPLETED,
    FAILED
}
