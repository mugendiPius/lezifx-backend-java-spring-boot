package com.lezifx.trading.service.admin;

import com.cloudinary.Cloudinary;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        if (cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank()) {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            config.put("api_key", apiKey);
            config.put("api_secret", apiSecret);
            this.cloudinary = new Cloudinary(config);
            log.info("Cloudinary initialized for cloud: {}", cloudName);
        } else {
            log.warn("Cloudinary not configured — upload will fail if attempted");
        }
    }

    private void requireConfigured() {
        if (cloudinary == null || cloudName == null || cloudName.isBlank()
                || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new BusinessException("CLOUDINARY_NOT_CONFIGURED",
                "Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET.");
        }
    }

    @SuppressWarnings("unchecked")
    public String uploadImage(MultipartFile file, String folder) {
        requireConfigured();
        try {
            byte[] bytes = file.getBytes();
            Map<String, Object> params = new HashMap<>();
            params.put("folder", folder);
            Map<String, Object> result = cloudinary.uploader().upload(bytes, params);
            return (String) result.get("secure_url");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Image upload failed: " + e.getMessage());
        }
    }

    /**
     * Generate a signed upload preset for direct browser-to-Cloudinary upload.
     * Frontend uses the returned signature + timestamp + apiKey + cloudName
     * to POST directly to Cloudinary's upload endpoint.
     */
    public Map<String, Object> generateUploadSignature(String folder) {
        requireConfigured();
        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            // Cloudinary signature: SHA-1 of "folder=<f>&timestamp=<t><apiSecret>"
            String toSign = "folder=" + folder + "&timestamp=" + timestamp + apiSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(toSign.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));

            Map<String, Object> result = new HashMap<>();
            result.put("signature", hex.toString());
            result.put("timestamp", timestamp);
            result.put("cloudName", cloudName);
            result.put("apiKey", apiKey);
            result.put("folder", folder);
            return result;
        } catch (Exception e) {
            log.error("Cloudinary signature generation failed: {}", e.getMessage());
            throw new BusinessException("SIGNATURE_FAILED", "Could not generate upload signature: " + e.getMessage());
        }
    }
}