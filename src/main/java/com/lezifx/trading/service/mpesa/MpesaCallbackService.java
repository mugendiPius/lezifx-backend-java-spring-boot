package com.lezifx.trading.service.mpesa;

import com.lezifx.trading.domain.enums.DepositStatus;
import com.lezifx.trading.domain.enums.MpesaCallbackType;
import com.lezifx.trading.domain.enums.WithdrawalStatus;
import com.lezifx.trading.domain.mpesa.DepositRequest;
import com.lezifx.trading.domain.mpesa.MpesaCallback;
import com.lezifx.trading.domain.mpesa.WithdrawalRequest;
import com.lezifx.trading.repository.DepositRequestRepository;
import com.lezifx.trading.repository.MpesaCallbackRepository;
import com.lezifx.trading.repository.WithdrawalRequestRepository;
import com.lezifx.trading.service.wallet.DepositService;
import com.lezifx.trading.service.wallet.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MpesaCallbackService {

    private final MpesaCallbackRepository mpesaCallbackRepository;
    private final DepositRequestRepository depositRequestRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final DepositService depositService;
    private final WithdrawalService withdrawalService;

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @SuppressWarnings("unchecked")
    public void handleC2bCallback(Map<String, Object> payload) {
        MpesaCallback callback = saveCallback(MpesaCallbackType.C2B_CONFIRM, payload);
        try {
            Map<String, Object> bodyMap = (Map<String, Object>) payload.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) bodyMap.get("stkCallback");

            int resultCode = toInt(stkCallback.get("ResultCode"));
            String resultDesc = String.valueOf(stkCallback.get("ResultDesc"));
            String merchantRequestId = (String) stkCallback.get("MerchantRequestID");
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");

            DepositRequest deposit = depositRequestRepository
                .findByCheckoutRequestId(checkoutRequestId).orElse(null);
            if (deposit == null) {
                deposit = depositRequestRepository
                    .findByMerchantRequestId(merchantRequestId).orElse(null);
            }

            if (deposit == null) {
                log.warn("C2B callback: no deposit found for checkoutRequestId={}", checkoutRequestId);
                callback.setProcessed(false);
                mpesaCallbackRepository.save(callback);
                return;
            }

            if (resultCode == 0) {
                Map<String, Object> metadata =
                    (Map<String, Object>) stkCallback.get("CallbackMetadata");
                List<Map<String, Object>> items =
                    (List<Map<String, Object>>) metadata.get("Item");

                String receiptNumber = null;
                String transactionDateStr = null;

                for (Map<String, Object> item : items) {
                    String name = (String) item.get("Name");
                    Object value = item.get("Value");
                    if ("MpesaReceiptNumber".equals(name)) receiptNumber = String.valueOf(value);
                    else if ("TransactionDate".equals(name)) transactionDateStr = String.valueOf(value);
                }

                Instant transactionDate = Instant.now();
                if (transactionDateStr != null) {
                    try {
                        transactionDate = LocalDateTime.parse(transactionDateStr, DATE_FORMATTER)
                            .toInstant(ZoneOffset.UTC);
                    } catch (Exception e) {
                        log.warn("Failed to parse TransactionDate '{}': {}", transactionDateStr, e.getMessage());
                    }
                }

                depositService.completeDeposit(deposit.getId(), receiptNumber, transactionDate);
                log.info("C2B deposit {} completed, receipt={}", deposit.getId(), receiptNumber);
            } else {
                deposit.setStatus(DepositStatus.FAILED);
                deposit.setFailureReason(resultDesc);
                depositRequestRepository.save(deposit);
                log.info("C2B deposit {} failed: code={}", deposit.getId(), resultCode);
            }

            callback.setProcessed(true);
        } catch (Exception e) {
            log.error("Error processing C2B callback: {}", e.getMessage(), e);
            callback.setProcessed(false);
        }
        mpesaCallbackRepository.save(callback);
    }

    @SuppressWarnings("unchecked")
    public void handleB2cResult(Map<String, Object> payload) {
        MpesaCallback callback = saveCallback(MpesaCallbackType.B2C_RESULT, payload);
        try {
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            int resultCode = toInt(result.get("ResultCode"));

            Map<String, Object> refData = (Map<String, Object>) result.get("ReferenceData");
            Map<String, Object> refItem = (Map<String, Object>) refData.get("ReferenceItem");
            UUID withdrawalId = UUID.fromString(String.valueOf(refItem.get("Value")));

            WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new RuntimeException("Withdrawal not found: " + withdrawalId));

            if (resultCode == 0) {
                String receipt = null;
                Map<String, Object> resultParams = (Map<String, Object>) result.get("ResultParameters");
                List<Map<String, Object>> paramList =
                    (List<Map<String, Object>>) resultParams.get("ResultParameter");
                for (Map<String, Object> param : paramList) {
                    if ("TransactionReceipt".equals(param.get("Key"))) {
                        receipt = String.valueOf(param.get("Value"));
                        break;
                    }
                }
                withdrawal.setStatus(WithdrawalStatus.COMPLETED);
                withdrawal.setMpesaReceiptNumber(receipt);
                withdrawal.setCompletedAt(Instant.now());
                withdrawalRequestRepository.save(withdrawal);
                log.info("B2C withdrawal {} completed, receipt={}", withdrawalId, receipt);
            } else {
                withdrawalService.refundFailedWithdrawal(withdrawalId);
                log.info("B2C withdrawal {} failed, refunded", withdrawalId);
            }

            callback.setProcessed(true);
        } catch (Exception e) {
            log.error("Error processing B2C result callback: {}", e.getMessage(), e);
            callback.setProcessed(false);
        }
        mpesaCallbackRepository.save(callback);
    }

    @SuppressWarnings("unchecked")
    public void handleB2cTimeout(Map<String, Object> payload) {
        MpesaCallback callback = saveCallback(MpesaCallbackType.B2C_TIMEOUT, payload);
        try {
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            if (result != null) {
                Object refDataObj = result.get("ReferenceData");
                if (refDataObj instanceof Map<?, ?> refData) {
                    Object refItemObj = ((Map<?, ?>) refData).get("ReferenceItem");
                    if (refItemObj instanceof Map<?, ?> refItem) {
                        UUID withdrawalId = UUID.fromString(
                            String.valueOf(((Map<?, ?>) refItem).get("Value")));
                        log.warn("B2C timeout for withdrawal {}, initiating refund", withdrawalId);
                        withdrawalService.refundFailedWithdrawal(withdrawalId);
                    }
                }
            }
            callback.setProcessed(true);
        } catch (Exception e) {
            log.error("Error processing B2C timeout: {}", e.getMessage(), e);
            callback.setProcessed(false);
        }
        mpesaCallbackRepository.save(callback);
    }

    private MpesaCallback saveCallback(MpesaCallbackType type, Map<String, Object> payload) {
        MpesaCallback callback = MpesaCallback.builder()
            .callbackType(type)
            .rawPayload(payload)
            .processed(false)
            .build();
        return mpesaCallbackRepository.save(callback);
    }

    private int toInt(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Long l) return l.intValue();
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}