package com.banco.yankiservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.banco.yankiservice.model.YankiOperation;
import com.banco.yankiservice.model.YankiTransaction;
import com.banco.yankiservice.model.YankiWallet;
import com.banco.yankiservice.service.YankiTransferResult;
import com.banco.yankiservice.service.YankiWalletService;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Controlador REST del monedero Yanki (Fase 12, Parte III). Expone sus
 * respuestas con RxJava ({@link Single}/{@link Flowable}), como el resto
 * de la funcionalidad nueva de Parte III; internamente delega en
 * {@link YankiWalletService}, que trabaja en Reactor.
 *
 * <p>Las operaciones que dependen de account-service ({@code link-debit-card},
 * {@code load}, {@code withdraw}) se resuelven via Kafka de forma
 * asincrona: este controlador responde {@code 202 Accepted} de inmediato
 * con la operacion en estado {@code PENDING}, y el resultado se consulta
 * despues con {@code GET /api/yanki/operations/{correlationId}}.</p>
 */
@RestController
@RequestMapping("/api/yanki")
@RequiredArgsConstructor
public class YankiWalletController {

    private final YankiWalletService walletService;

    /** Registra un monedero nuevo; no requiere ser cliente del banco. */
    @PostMapping("/wallets")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<YankiWallet> register(@Valid @RequestBody YankiWallet wallet) {
        return RxJava3Adapter.monoToSingle(walletService.register(wallet));
    }

    /** Obtiene un monedero por su identificador. */
    @GetMapping("/wallets/{id}")
    public Single<YankiWallet> findById(@PathVariable String id) {
        return RxJava3Adapter.monoToSingle(walletService.findById(id));
    }

    /** Envia saldo de un monedero a otro, identificados por su numero de celular. */
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<YankiTransferResult> transfer(@Valid @RequestBody TransferRequest request) {
        return RxJava3Adapter.monoToSingle(
                walletService.transfer(request.senderPhone(), request.receiverPhone(), request.amount()));
    }

    /** Inicia (via Kafka) la asociacion del monedero a una tarjeta de debito de account-service. */
    @PostMapping("/wallets/{id}/link-debit-card")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Single<YankiOperation> linkDebitCard(@PathVariable String id, @Valid @RequestBody LinkDebitCardRequest request) {
        return RxJava3Adapter.monoToSingle(walletService.linkDebitCard(id, request.debitCardId()));
    }

    /** Inicia (via Kafka) una carga de saldo desde la cuenta principal vinculada. */
    @PostMapping("/wallets/{id}/load")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Single<YankiOperation> load(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return RxJava3Adapter.monoToSingle(walletService.load(id, request.amount()));
    }

    /** Inicia (via Kafka) un retiro de saldo hacia la cuenta principal vinculada. */
    @PostMapping("/wallets/{id}/withdraw")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Single<YankiOperation> withdraw(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return RxJava3Adapter.monoToSingle(walletService.withdraw(id, request.amount()));
    }

    /** Consulta el estado de una operacion asincrona (link/carga/retiro) iniciada previamente. */
    @GetMapping("/operations/{correlationId}")
    public Single<YankiOperation> getOperation(@PathVariable String correlationId) {
        return RxJava3Adapter.monoToSingle(walletService.getOperation(correlationId));
    }

    /** Consulta el historial de movimientos del monedero. */
    @GetMapping("/wallets/{id}/movements")
    public Flowable<YankiTransaction> getMovements(@PathVariable String id) {
        return RxJava3Adapter.fluxToFlowable(walletService.getMovements(id));
    }
}
