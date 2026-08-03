package com.banco.yankiservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.yankiservice.model.DocumentType;
import com.banco.yankiservice.model.YankiOperation;
import com.banco.yankiservice.model.YankiOperationStatus;
import com.banco.yankiservice.model.YankiOperationType;
import com.banco.yankiservice.model.YankiWallet;
import com.banco.yankiservice.repository.YankiOperationRepository;
import com.banco.yankiservice.repository.YankiTransactionRepository;
import com.banco.yankiservice.repository.YankiWalletRepository;

import reactor.core.publisher.Mono;

/**
 * Pruebas unitarias de {@link YankiAccountResponseListener} (Fase 12): se
 * invoca el metodo {@code @KafkaListener} directamente con un evento de
 * prueba (no es un endpoint HTTP) y se verifica el efecto sobre el
 * monedero y la operacion.
 */
@ExtendWith(MockitoExtension.class)
class YankiAccountResponseListenerTest {

    @Mock
    private YankiOperationRepository operationRepository;

    @Mock
    private YankiWalletRepository walletRepository;

    @Mock
    private YankiTransactionRepository transactionRepository;

    private YankiAccountResponseListener listener;

    private YankiWallet wallet() {
        return YankiWallet.builder()
                .id("wallet-1")
                .documentType(DocumentType.DNI)
                .documentNumber("45678912")
                .phoneNumber("999111222")
                .imei("123456789012345")
                .email("user@test.com")
                .balance(new BigDecimal("100.00"))
                .build();
    }

    private YankiOperation operation(YankiOperationType type) {
        return YankiOperation.builder()
                .id("op-1")
                .correlationId("corr-1")
                .walletId("wallet-1")
                .operationType(type)
                .amount(new BigDecimal("30.00"))
                .build();
    }

    void setUp() {
        listener = new YankiAccountResponseListener(operationRepository, walletRepository, transactionRepository);
    }

    @Test
    void linkCardExitosoActualizaElMonederoYCompletaLaOperacion() {
        setUp();
        YankiOperation op = operation(YankiOperationType.LINK_CARD);
        op.setDebitCardId("card-1");
        YankiWallet wallet = wallet();

        when(operationRepository.findByCorrelationId("corr-1")).thenReturn(Mono.just(op));
        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(wallet));
        when(walletRepository.save(wallet)).thenReturn(Mono.just(wallet));
        when(operationRepository.save(op)).thenReturn(Mono.just(op));

        listener.onResponse(new YankiAccountResponseEvent(
                "corr-1", "wallet-1", YankiOperationType.LINK_CARD, true, "acc-1", null, null));

        assertThat(wallet.getLinkedDebitCardId()).isEqualTo("card-1");
        assertThat(wallet.getLinkedAccountId()).isEqualTo("acc-1");
        assertThat(op.getStatus()).isEqualTo(YankiOperationStatus.COMPLETED);
    }

    @Test
    void creditExitosoAumentaElSaldoYRegistraElMovimientoDeCarga() {
        setUp();
        YankiOperation op = operation(YankiOperationType.CREDIT);
        YankiWallet wallet = wallet();

        when(operationRepository.findByCorrelationId("corr-1")).thenReturn(Mono.just(op));
        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(wallet));
        when(walletRepository.save(wallet)).thenReturn(Mono.just(wallet));
        when(transactionRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(operationRepository.save(op)).thenReturn(Mono.just(op));

        listener.onResponse(new YankiAccountResponseEvent(
                "corr-1", "wallet-1", YankiOperationType.CREDIT, true, "acc-1", new BigDecimal("70.00"), null));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("130.00"));
        assertThat(op.getStatus()).isEqualTo(YankiOperationStatus.COMPLETED);
    }

    @Test
    void debitExitosoDisminuyeElSaldoYRegistraElMovimientoDeRetiro() {
        setUp();
        YankiOperation op = operation(YankiOperationType.DEBIT);
        YankiWallet wallet = wallet();

        when(operationRepository.findByCorrelationId("corr-1")).thenReturn(Mono.just(op));
        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(wallet));
        when(walletRepository.save(wallet)).thenReturn(Mono.just(wallet));
        when(transactionRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(operationRepository.save(op)).thenReturn(Mono.just(op));

        listener.onResponse(new YankiAccountResponseEvent(
                "corr-1", "wallet-1", YankiOperationType.DEBIT, true, "acc-1", new BigDecimal("130.00"), null));

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(op.getStatus()).isEqualTo(YankiOperationStatus.COMPLETED);
    }

    @Test
    void respuestaFallidaMarcaLaOperacionComoFailedSinTocarElMonedero() {
        setUp();
        YankiOperation op = operation(YankiOperationType.DEBIT);
        when(operationRepository.findByCorrelationId("corr-1")).thenReturn(Mono.just(op));
        when(operationRepository.save(op)).thenReturn(Mono.just(op));

        listener.onResponse(new YankiAccountResponseEvent(
                "corr-1", "wallet-1", YankiOperationType.DEBIT, false, null, null, "Fondos insuficientes"));

        assertThat(op.getStatus()).isEqualTo(YankiOperationStatus.FAILED);
        assertThat(op.getErrorMessage()).isEqualTo("Fondos insuficientes");
        verify(walletRepository, never()).findById(any(String.class));
    }
}
