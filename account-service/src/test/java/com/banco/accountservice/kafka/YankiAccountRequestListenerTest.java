package com.banco.accountservice.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.DebitCard;
import com.banco.accountservice.model.DebitCardStatus;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.service.AccountService;
import com.banco.accountservice.service.DebitCardService;

import reactor.core.publisher.Mono;

/**
 * Pruebas unitarias de {@link YankiAccountRequestListener} (Fase 12): se
 * invoca el metodo {@code @KafkaListener} directamente con un evento de
 * prueba (no es un endpoint HTTP) y se verifica el evento de respuesta
 * publicado.
 */
@ExtendWith(MockitoExtension.class)
class YankiAccountRequestListenerTest {

    @Mock
    private DebitCardService debitCardService;

    @Mock
    private AccountService accountService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private YankiAccountRequestListener listener;

    private void setUp() {
        listener = new YankiAccountRequestListener(debitCardService, accountService, kafkaTemplate);
    }

    private SavingsAccount account(BigDecimal balance) {
        SavingsAccount account = SavingsAccount.builder()
                .customerId("customer-1").currency("PEN").balance(balance)
                .monthlyMovementLimit(5).freeMonthlyTransactionLimit(3)
                .transactionFeeAmount(BigDecimal.ZERO).build();
        account.setId("acc-1");
        return account;
    }

    @SuppressWarnings("unchecked")
    private YankiAccountResponseEvent capturePublishedResponse() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(YankiAccountRequestListener.RESPONSE_TOPIC), any(), captor.capture());
        return (YankiAccountResponseEvent) captor.getValue();
    }

    @Test
    void linkCardExitosoPublicaRespuestaConLaCuentaDeLaTarjeta() {
        setUp();
        DebitCard card = DebitCard.builder().accountId("acc-1").customerId("customer-1")
                .status(DebitCardStatus.ACTIVE).build();
        card.setId("card-1");
        when(debitCardService.findById("card-1")).thenReturn(Mono.just(card));

        listener.onRequest(new YankiAccountRequestEvent(
                "corr-1", "wallet-1", YankiOperationType.LINK_CARD, "card-1", null, null));

        YankiAccountResponseEvent response = capturePublishedResponse();
        org.assertj.core.api.Assertions.assertThat(response.success()).isTrue();
        org.assertj.core.api.Assertions.assertThat(response.accountId()).isEqualTo("acc-1");
    }

    @Test
    void linkCardConTarjetaBloqueadaPublicaRespuestaFallida() {
        setUp();
        DebitCard card = DebitCard.builder().accountId("acc-1").customerId("customer-1")
                .status(DebitCardStatus.BLOCKED).build();
        card.setId("card-1");
        when(debitCardService.findById("card-1")).thenReturn(Mono.just(card));

        listener.onRequest(new YankiAccountRequestEvent(
                "corr-1", "wallet-1", YankiOperationType.LINK_CARD, "card-1", null, null));

        YankiAccountResponseEvent response = capturePublishedResponse();
        org.assertj.core.api.Assertions.assertThat(response.success()).isFalse();
    }

    // CREDIT acredita el MONEDERO (carga), lo que implica debitar la cuenta vinculada via tarjeta de debito.
    @Test
    void creditDelMonederoDebitaLaCuentaViaTarjetaDeDebitoYPublicaElNuevoSaldo() {
        setUp();
        when(accountService.payWithDebitCard(eq("acc-1"), any(BigDecimal.class)))
                .thenReturn(Mono.just(AccountMovement.builder().build()));
        when(accountService.findById("acc-1")).thenReturn(Mono.just(account(new BigDecimal("70.00"))));

        listener.onRequest(new YankiAccountRequestEvent(
                "corr-1", "wallet-1", YankiOperationType.CREDIT, null, "acc-1", new BigDecimal("30.00")));

        YankiAccountResponseEvent response = capturePublishedResponse();
        org.assertj.core.api.Assertions.assertThat(response.success()).isTrue();
        org.assertj.core.api.Assertions.assertThat(response.newAccountBalance()).isEqualByComparingTo("70.00");
    }

    // DEBIT debita el MONEDERO (retiro), lo que implica acreditar (depositar en) la cuenta vinculada.
    @Test
    void debitDelMonederoAcreditaLaCuentaConUnDepositoYPublicaElNuevoSaldo() {
        setUp();
        when(accountService.deposit(eq("acc-1"), any(BigDecimal.class)))
                .thenReturn(Mono.just(AccountMovement.builder().build()));
        when(accountService.findById("acc-1")).thenReturn(Mono.just(account(new BigDecimal("130.00"))));

        listener.onRequest(new YankiAccountRequestEvent(
                "corr-1", "wallet-1", YankiOperationType.DEBIT, null, "acc-1", new BigDecimal("30.00")));

        YankiAccountResponseEvent response = capturePublishedResponse();
        org.assertj.core.api.Assertions.assertThat(response.success()).isTrue();
        org.assertj.core.api.Assertions.assertThat(response.newAccountBalance()).isEqualByComparingTo("130.00");
    }

    @Test
    void creditConFondosInsuficientesEnLaCuentaPublicaRespuestaFallida() {
        setUp();
        // findById no deberia alcanzarse (payWithDebitCard corta antes con error),
        // pero se stubea igual por la evaluacion eager de argumentos en Java (ver CLAUDE.md).
        org.mockito.Mockito.lenient().when(accountService.findById("acc-1")).thenReturn(Mono.just(account(BigDecimal.ZERO)));
        when(accountService.payWithDebitCard(eq("acc-1"), any(BigDecimal.class))).thenReturn(
                Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Fondos insuficientes")));

        listener.onRequest(new YankiAccountRequestEvent(
                "corr-1", "wallet-1", YankiOperationType.CREDIT, null, "acc-1", new BigDecimal("999.00")));

        YankiAccountResponseEvent response = capturePublishedResponse();
        org.assertj.core.api.Assertions.assertThat(response.success()).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.errorMessage()).contains("Fondos insuficientes");
    }
}
