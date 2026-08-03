package com.banco.accountservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.DebitCard;
import com.banco.accountservice.service.DebitCardService;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Controlador REST de tarjetas de debito (Fase 11, Parte III). A
 * diferencia de los controladores de Parte I/II (Reactor puro), expone
 * sus respuestas con RxJava ({@link Single}/{@link Flowable}), como pide
 * la consigna para la funcionalidad nueva; internamente sigue delegando
 * en {@link DebitCardService}, que trabaja en Reactor como el resto del
 * servicio.
 */
@RestController
@RequestMapping("/api/debit-cards")
@RequiredArgsConstructor
public class DebitCardController {

    private final DebitCardService debitCardService;

    /** Emite una tarjeta de debito para una cuenta bancaria existente del mismo cliente. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<DebitCard> issue(@Valid @RequestBody DebitCard debitCard) {
        return RxJava3Adapter.monoToSingle(debitCardService.issue(debitCard));
    }

    /** Obtiene todas las tarjetas de debito registradas. */
    @GetMapping
    public Flowable<DebitCard> findAll() {
        return RxJava3Adapter.fluxToFlowable(debitCardService.findAll());
    }

    /** Obtiene una tarjeta de debito por su identificador. */
    @GetMapping("/{id}")
    public Single<DebitCard> findById(@PathVariable String id) {
        return RxJava3Adapter.monoToSingle(debitCardService.findById(id));
    }

    /** Registra un pago con la tarjeta, cargado como retiro a la cuenta vinculada. */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<AccountMovement> pay(@PathVariable String id, @Valid @RequestBody MovementRequest request) {
        return RxJava3Adapter.monoToSingle(debitCardService.pay(id, request.amount()));
    }

    /** Consulta el historial de pagos realizados con la tarjeta. */
    @GetMapping("/{id}/movements")
    public Flowable<AccountMovement> getMovements(@PathVariable String id) {
        return RxJava3Adapter.fluxToFlowable(debitCardService.getMovements(id));
    }
}
