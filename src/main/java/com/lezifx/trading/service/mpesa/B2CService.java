package com.lezifx.trading.service.mpesa;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.domain.enums.WithdrawalStatus;
import com.lezifx.trading.domain.mpesa.WithdrawalRequest;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.WithdrawalRequestRepository;
import com.lezifx.trading.service.wallet.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2CService {

    private final DarajaAuthService darajaAuthService;
    private final TenantRepository tenantRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalService withdrawalService;
    private final AesEncryptionService aesEncryptionService;
    private final RestTemplate restTemplate;

    @Value("${daraja.callback-base-url}")
    private String callbackBaseUrl;

    private static final int    B2C_MAX_RETRIES     = 3;
    private static final long[] B2C_RETRY_DELAYS_MS = {0L, 1_000L, 3_000L};

    public void initiateB2c(UUID withdrawalId) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
            .orElseThrow(() -> new RuntimeException("Withdrawal not found: " + withdrawalId));

        Tenant tenant = tenantRepository.findById(withdrawal.getTenant().getId())
            .orElseThrow(() -> new RuntimeException("Tenant not found"));

        String accessToken;
        try {
            accessToken = darajaAuthService.getAccessToken(tenant.getId());
        } catch (Exception e) {
            log.error("B2C auth error for withdrawal {}: {}", withdrawalId, e.getMessage());
            handleB2cFailure(withdrawal, withdrawalId, e.getMessage());
            return;
        }

        String baseUrl       = darajaAuthService.getBaseUrl(tenant);
        String shortcode     = tenant.getDarajaShortcode();
        String initiatorName = aesEncryptionService.decrypt(tenant.getDarajaB2cInitiatorName());
        String securityCred  = aesEncryptionService.decrypt(tenant.getDarajaB2cSecurityCred());
        String phone         = formatPhone(withdrawal.getPhoneNumber());
        int    amount        = withdrawal.getAmount().intValue();
        String originatorConversationId = UUID.randomUUID().toString();

        Exception lastNetworkError = null;

        for (int attempt = 0; attempt < B2C_MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(B2C_RETRY_DELAYS_MS[attempt]); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                log.info("B2C retry {}/{} for withdrawal {}", attempt + 1, B2C_MAX_RETRIES, withdrawalId);
            }

            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("OriginatorConversationID", originatorConversationId);
                body.put("InitiatorName", initiatorName);
                body.put("SecurityCredential", securityCred);
                body.put("CommandID", "BusinessPayment");
                body.put("Amount", amount);
                body.put("PartyA", shortcode);
                body.put("PartyB", phone);
                body.put("Remarks", "Withdrawal from " + tenant.getBrandName());
                body.put("QueueTimeOutURL", callbackBaseUrl + "/api/v1/mpesa/b2c/timeout");
                body.put("ResultURL", callbackBaseUrl + "/api/v1/mpesa/b2c/result");
                body.put("Occasion", withdrawalId.toString());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/mpesa/b2c/v3/paymentrequest",
                    new HttpEntity<>(body, headers), Map.class);

                if (response != null && "0".equals(String.valueOf(response.get("ResponseCode")))) {
                    withdrawal.setConversationId((String) response.get("ConversationID"));
                    withdrawal.setOriginatorConversationId(originatorConversationId);
                    withdrawal.setStatus(WithdrawalStatus.PROCESSING);
                    withdrawalRequestRepository.save(withdrawal);
                    log.info("B2C initiated for withdrawal {} on attempt {}", withdrawalId, attempt + 1);
                    lastNetworkError = null;
                    break;
                } else {
                    String errorMsg = response != null
                        ? String.valueOf(response.get("ResponseDescription")) : "Null response";
                    log.warn("B2C rejected by Safaricom for withdrawal {}: {}", withdrawalId, errorMsg);
                    handleB2cFailure(withdrawal, withdrawalId, errorMsg);
                    lastNetworkError = null;
                    break;
                }
            } catch (Exception e) {
                lastNetworkError = e;
                log.warn("B2C network error attempt {}/{} for withdrawal {}: {}",
                    attempt + 1, B2C_MAX_RETRIES, withdrawalId, e.getMessage());
            }
        }

        if (lastNetworkError != null) {
            log.error("B2C failed after {} attempts for withdrawal {}: {}",
                B2C_MAX_RETRIES, withdrawalId, lastNetworkError.getMessage());
            handleB2cFailure(withdrawal, withdrawalId,
                "Payment gateway unavailable after retries. Your balance has been refunded.");
        }
    }

    private void handleB2cFailure(WithdrawalRequest withdrawal, UUID withdrawalId, String reason) {
        WithdrawalStatus priorStatus = withdrawal.getStatus();
        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setFailureReason(reason);
        withdrawalRequestRepository.save(withdrawal);

        if (priorStatus == WithdrawalStatus.APPROVED || priorStatus == WithdrawalStatus.PROCESSING) {
            try {
                withdrawalService.refundFailedWithdrawal(withdrawalId);
            } catch (Exception refundEx) {
                log.error("Refund failed for withdrawal {}: {}", withdrawalId, refundEx.getMessage());
            }
        }
    }

    private String formatPhone(String phone) {
        if (phone == null) return phone;
        String cleaned = phone.trim();
        if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);
        if (cleaned.startsWith("07") || cleaned.startsWith("01"))
            cleaned = "254" + cleaned.substring(1);
        return cleaned;
    }
}