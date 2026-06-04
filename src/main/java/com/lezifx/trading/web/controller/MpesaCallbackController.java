package com.lezifx.trading.web.controller;

import com.lezifx.trading.service.mpesa.MpesaCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/mpesa")
@RequiredArgsConstructor
public class MpesaCallbackController {

    private final MpesaCallbackService mpesaCallbackService;

    @PostMapping("/c2b/callback")
    public ResponseEntity<Map<String, String>> c2bCallback(
            @RequestBody Map<String, Object> payload) {
        mpesaCallbackService.handleC2bCallback(payload);
        return ResponseEntity.ok(Map.of(
                "ResultCode", "00",
                "ResultDesc", "Accepted"));
    }

    @PostMapping("/b2c/result")
    public ResponseEntity<Map<String, String>> b2cResult(
            @RequestBody Map<String, Object> payload) {
        mpesaCallbackService.handleB2cResult(payload);
        return ResponseEntity.ok(Map.of(
                "ResultCode", "00",
                "ResultDesc", "Accepted"));
    }

    @PostMapping("/b2c/timeout")
    public ResponseEntity<Map<String, String>> b2cTimeout(
            @RequestBody Map<String, Object> payload) {
        mpesaCallbackService.handleB2cTimeout(payload);
        return ResponseEntity.ok(Map.of(
                "ResultCode", "00",
                "ResultDesc", "Accepted"));
    }
}