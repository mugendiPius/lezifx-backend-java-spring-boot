package com.lezifx.trading.domain.wallet;

public enum TransactionType {
    // Real player transactions
    DEPOSIT,
    WITHDRAWAL,
    TRADE_STAKE,
    TRADE_WIN,
    TRADE_LOSS,
    TRADE_REFUND,
    ADMIN_ADJUSTMENT,
    DEMO_REFILL,
    // Marketer mock transactions
    MOCK_DEPOSIT,
    MOCK_WITHDRAWAL,
    MARKETER_TRADE_WIN,
    MARKETER_TRADE_LOSS
}