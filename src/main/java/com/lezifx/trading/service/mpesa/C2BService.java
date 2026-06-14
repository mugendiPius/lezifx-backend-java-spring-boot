package com.lezifx.trading.service.mpesa;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.domain.enums.DepositStatus;
import com.lezifx.trading.domain.mpesa.DepositRequest;
import com.lezifx.trading.repository.DepositRequestRepository;
import com.lezifx.trading.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class C2BService {

    private final DarajaAuthService darajaAuthService;
    private final TenantRepository tenantRepository;
    private final DepositRequestRepository depositRequestRepository;
    private final AesEncryptionService aesEncryptionService;
    private final RestTemplate restTemplate;

    @Value("${daraja.callback-base-url}")
    private String callbackBaseUrl;

    private static final DateTimeFormatter TS_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public void initiateStk(UUID depositId) {
        DepositRequest deposit = depositRequestRepository.findById(depositId)
            .orElseThrow(() -> new RuntimeException("Deposit not found: " + depositId));

        Tenant tenant = tenantRepository.findById(deposit.getTenant().getId())
            .orElseThrow(() -> new RuntimeException("Tenant not found"));

        try {
            String accessToken = darajaAuthService.getAccessToken(tenant.getId());
            String baseUrl = darajaAuthService.getBaseUrl(tenant);

            String shortcode = tenant.getDarajaShortcode();
            String passkey = aesEncryptionService.decrypt(tenant.getDarajaPasskey());

            String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(TS_FORMATTER);
            String rawPassword = shortcode + passkey + timestamp;
            String password = Base64.getEncoder().encodeToString(rawPassword.getBytes());

            String phone = formatPhone(deposit.getPhoneNumber());
            String callbackUrl = callbackBaseUrl + "/api/v1/mpesa/c2b/callback";
            int amount = deposit.getAmount().intValue();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("BusinessShortCode", shortcode);
            body.put("Password", password);
            body.put("Timestamp", timestamp);
            body.put("TransactionType", "CustomerPayBillOnline");
            body.put("Amount", amount);
            body.put("PartyA", phone);
            body.put("PartyB", shortcode);
            body.put("PhoneNumber", phone);
            body.put("CallBackURL", callbackUrl);
            body.put("AccountReference", depositId.toString());
            body.put("TransactionDesc", "Deposit to " + tenant.getBrandName());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                baseUrl + "/mpesa/stkpush/v1/processrequest", entity, Map.class);

            if (response != null && "0".equals(String.valueOf(response.get("ResponseCode")))) {
                deposit.setMerchantRequestId((String) response.get("MerchantRequestID"));
                deposit.setCheckoutRequestId((String) response.get("CheckoutRequestID"));
                log.info("STK push initiated for deposit {}", depositId);
            } else {
                String errorMsg = response != null
                    ? String.valueOf(response.get("ResponseDescription")) : "Null response";
                log.warn("STK push non-zero response for deposit {}: {}", depositId, errorMsg);
                deposit.setStatus(DepositStatus.FAILED);
                deposit.setFailureReason(errorMsg);
            }
        } catch (com.lezifx.trading.web.exception.BusinessException e) {
            // Configuration or auth errors — mark failed and propagate so caller can surface to user
            log.error("STK push config/auth error for deposit {}: {}", depositId, e.getMessage());
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setFailureReason(e.getMessage());
            depositRequestRepository.save(deposit);
            throw e;
        } catch (Exception e) {
            log.error("STK push network error for deposit {}: {}", depositId, e.getMessage());
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setFailureReason(e.getMessage());
        }

        depositRequestRepository.save(deposit);
    }

    public String formatPhone(String phone) {
        if (phone == null) return phone;
        String cleaned = phone.trim();
        if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);
        if (cleaned.startsWith("07") || cleaned.startsWith("01"))
            cleaned = "254" + cleaned.substring(1);
        return cleaned;
    }
}