package com.banco.yankiservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.yankiservice.kafka.YankiAccountRequestEvent;
import com.banco.yankiservice.kafka.YankiEventPublisher;
import com.banco.yankiservice.model.DocumentType;
import com.banco.yankiservice.model.YankiOperation;
import com.banco.yankiservice.model.YankiOperationType;
import com.banco.yankiservice.model.YankiWallet;
import com.banco.yankiservice.repository.YankiOperationRepository;
import com.banco.yankiservice.repository.YankiTransactionRepository;
import com.banco.yankiservice.repository.YankiWalletRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link YankiWalletServiceImpl} (Fase 12).
 */
@ExtendWith(MockitoExtension.class)
class YankiWalletServiceImplTest {

    @Mock
    private YankiWalletRepository walletRepository;

    @Mock
    private YankiTransactionRepository transactionRepository;

    @Mock
    private YankiOperationRepository operationRepository;

    @Mock
    private YankiEventPublisher eventPublisher;

    private YankiWalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletService = new YankiWalletServiceImpl(
                walletRepository, transactionRepository, operationRepository, eventPublisher);
    }

    private YankiWallet wallet(String phone) {
        return YankiWallet.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("45678912")
                .phoneNumber(phone)
                .imei("123456789012345")
                .email("user@test.com")
                .balance(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void registerCreaElMonederoCuandoDocumentoYCelularSonUnicos() {
        YankiWallet request = wallet("999111222");
        YankiWallet saved = wallet("999111222");
        saved.setId("wallet-1");

        when(walletRepository.existsByDocumentNumber("45678912")).thenReturn(Mono.just(false));
        when(walletRepository.existsByPhoneNumber("999111222")).thenReturn(Mono.just(false));
        when(walletRepository.save(request)).thenReturn(Mono.just(saved));

        StepVerifier.create(walletService.register(request))
                .expectNextMatches(w -> w.getId().equals("wallet-1"))
                .verifyComplete();
    }

    @Test
    void registerRechazaDocumentoDuplicado() {
        YankiWallet request = wallet("999111222");
        // El argumento de .then(...) se evalua en Java antes de subscribirse
        // (ver nota en CLAUDE.md), asi que se stubea igual aunque no deberia alcanzarse.
        lenient().when(walletRepository.save(request)).thenReturn(Mono.just(request));
        when(walletRepository.existsByDocumentNumber("45678912")).thenReturn(Mono.just(true));

        StepVerifier.create(walletService.register(request))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409
                        && rse.getReason().contains("documento"))
                .verify();
    }

    @Test
    void registerRechazaCelularDuplicado() {
        YankiWallet request = wallet("999111222");
        lenient().when(walletRepository.save(request)).thenReturn(Mono.just(request));
        when(walletRepository.existsByDocumentNumber("45678912")).thenReturn(Mono.just(false));
        when(walletRepository.existsByPhoneNumber("999111222")).thenReturn(Mono.just(true));

        StepVerifier.create(walletService.register(request))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409
                        && rse.getReason().contains("celular"))
                .verify();
    }

    @Test
    void transferMueveSaldoEntreDosMonederosPorNumeroDeCelular() {
        YankiWallet sender = wallet("111");
        sender.setId("wallet-1");
        YankiWallet receiver = wallet("222");
        receiver.setId("wallet-2");
        receiver.setBalance(new BigDecimal("10.00"));

        when(walletRepository.findByPhoneNumber("111")).thenReturn(Mono.just(sender));
        when(walletRepository.findByPhoneNumber("222")).thenReturn(Mono.just(receiver));
        when(walletRepository.save(sender)).thenReturn(Mono.just(sender));
        when(walletRepository.save(receiver)).thenReturn(Mono.just(receiver));
        when(transactionRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(walletService.transfer("111", "222", new BigDecimal("30.00")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void transferRechazaCuandoElEmisorYElReceptorSonElMismo() {
        StepVerifier.create(walletService.transfer("111", "111", new BigDecimal("10.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void transferRechazaSaldoInsuficiente() {
        YankiWallet sender = wallet("111");
        sender.setId("wallet-1");
        sender.setBalance(new BigDecimal("10.00"));
        YankiWallet receiver = wallet("222");
        receiver.setId("wallet-2");

        when(walletRepository.findByPhoneNumber("111")).thenReturn(Mono.just(sender));
        when(walletRepository.findByPhoneNumber("222")).thenReturn(Mono.just(receiver));

        StepVerifier.create(walletService.transfer("111", "222", new BigDecimal("50.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("insuficiente"))
                .verify();
    }

    @Test
    void linkDebitCardCreaLaOperacionPendienteYPublicaLaSolicitud() {
        YankiWallet w = wallet("111");
        w.setId("wallet-1");

        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(w));
        when(operationRepository.save(any(YankiOperation.class))).thenAnswer(invocation -> {
            YankiOperation op = invocation.getArgument(0);
            op.setId("op-1");
            return Mono.just(op);
        });

        StepVerifier.create(walletService.linkDebitCard("wallet-1", "card-1"))
                .expectNextMatches(op -> op.getOperationType() == YankiOperationType.LINK_CARD
                        && op.getDebitCardId().equals("card-1"))
                .verifyComplete();

        verify(eventPublisher).publishRequest(any(YankiAccountRequestEvent.class));
    }

    @Test
    void loadRechazaCuandoElMonederoNoTieneCuentaVinculada() {
        YankiWallet w = wallet("111");
        w.setId("wallet-1");
        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(w));

        StepVerifier.create(walletService.load("wallet-1", new BigDecimal("50.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("vinculada"))
                .verify();

        verify(eventPublisher, never()).publishRequest(any());
    }

    @Test
    void withdrawRechazaSaldoInsuficienteEnElMonedero() {
        YankiWallet w = wallet("111");
        w.setId("wallet-1");
        w.setLinkedAccountId("acc-1");
        w.setBalance(new BigDecimal("10.00"));
        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(w));

        StepVerifier.create(walletService.withdraw("wallet-1", new BigDecimal("50.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("insuficiente"))
                .verify();
    }

    @Test
    void loadCreaLaOperacionPendienteYPublicaLaSolicitudConLaCuentaVinculada() {
        YankiWallet w = wallet("111");
        w.setId("wallet-1");
        w.setLinkedAccountId("acc-1");
        when(walletRepository.findById("wallet-1")).thenReturn(Mono.just(w));
        when(operationRepository.save(any(YankiOperation.class))).thenAnswer(invocation -> {
            YankiOperation op = invocation.getArgument(0);
            op.setId("op-1");
            return Mono.just(op);
        });

        StepVerifier.create(walletService.load("wallet-1", new BigDecimal("50.00")))
                .expectNextMatches(op -> op.getOperationType() == YankiOperationType.CREDIT
                        && op.getAmount().compareTo(new BigDecimal("50.00")) == 0)
                .verifyComplete();

        verify(eventPublisher).publishRequest(any(YankiAccountRequestEvent.class));
    }

    @Test
    void getOperationPropagaNotFoundCuandoNoExiste() {
        when(operationRepository.findByCorrelationId("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(walletService.getOperation("no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }
}
