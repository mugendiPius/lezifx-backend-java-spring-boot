package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.PlatformMode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PriceGeneratorService {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();

    private static final Map<String, Double> VOLATILITY_MAP = Map.ofEntries(
        Map.entry("MAI/KES",  0.018),
        Map.entry("AVO/KES",  0.032),
        Map.entry("CHI/KES",  0.022),
        Map.entry("UGL/KES",  0.015),
        Map.entry("NYC/KES",  0.028),
        Map.entry("MTU/KES",  0.012),
        Map.entry("MBG/KES",  0.025),
        Map.entry("SKM/KES",  0.040),
        Map.entry("CFE/KES",  0.035),
        Map.entry("BNN/KES",  0.030),
        Map.entry("BTC/USDT", 0.022),
        Map.entry("ETH/USDT", 0.026),
        Map.entry("SOL/USDT", 0.038),
        Map.entry("BNB/USDT", 0.030)
    );

    private static final Map<String, BigDecimal> BASE_PRICES = Map.ofEntries(
        Map.entry("MAI/KES",  BigDecimal.valueOf(65)),
        Map.entry("AVO/KES",  BigDecimal.valueOf(42)),
        Map.entry("CHI/KES",  BigDecimal.valueOf(280)),
        Map.entry("UGL/KES",  BigDecimal.valueOf(130)),
        Map.entry("NYC/KES",  BigDecimal.valueOf(800)),
        Map.entry("MTU/KES",  BigDecimal.valueOf(50)),
        Map.entry("MBG/KES",  BigDecimal.valueOf(7500)),
        Map.entry("SKM/KES",  BigDecimal.valueOf(15)),
        Map.entry("CFE/KES",  BigDecimal.valueOf(510)),
        Map.entry("BNN/KES",  BigDecimal.valueOf(20)),
        Map.entry("BTC/USDT", BigDecimal.valueOf(8700000)),
        Map.entry("ETH/USDT", BigDecimal.valueOf(460000)),
        Map.entry("SOL/USDT", BigDecimal.valueOf(145)),
        Map.entry("BNB/USDT", BigDecimal.valueOf(580))
    );

    public BigDecimal computeSealedExitPrice(String symbol, BigDecimal entryPrice,
                                              int durationSeconds, PlatformMode mode,
                                              int activeSessionCount, BigDecimal houseRatio) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double volatility = VOLATILITY_MAP.getOrDefault(symbol, 0.025);
        double timeFactor = Math.sqrt(durationSeconds / 30.0);

        double winBias = switch (mode) {
            case WIN    -> 0.62;
            case NORMAL -> 0.47;
            case LOSE   -> 0.30;
        };

        double ratio = houseRatio.doubleValue();
        if (ratio < 1.20) winBias = Math.min(winBias, 0.35);
        if (ratio < 0.50) winBias = 0.18;

        double volumeAdj = Math.min(activeSessionCount / 300.0, 0.12);
        winBias = winBias - volumeAdj * (winBias - 0.47);

        boolean playerWins = rng.nextDouble() < winBias;
        double move = volatility * timeFactor * (0.3 + rng.nextDouble() * 0.7);
        double multiplier = playerWins ? (1.0 + move) : (1.0 - move);
        multiplier = Math.max(multiplier, 0.05);

        BigDecimal result = entryPrice.multiply(BigDecimal.valueOf(multiplier), MC);
        return result.setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal generateBridgeTick(BigDecimal currentPrice, BigDecimal sealedExitPrice,
                                          int ticksRemaining, double volatility) {
        if (ticksRemaining <= 0) return sealedExitPrice;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double current = currentPrice.doubleValue();
        double sealed  = sealedExitPrice.doubleValue();

        double drift = (sealed - current) / ticksRemaining;
        double gaussian = rng.nextGaussian();
        double noise = gaussian * volatility * current * 0.4;
        double next = current + drift + noise;
        next = Math.max(next, current * 0.01);

        return BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal getCurrentPrice(UUID tenantId, String symbol) {
        String key = tenantId.toString() + ":" + symbol;
        BigDecimal cached = currentPrices.get(key);
        if (cached != null) return cached;
        return BASE_PRICES.getOrDefault(symbol, BigDecimal.valueOf(100));
    }

    public void updateCurrentPrice(UUID tenantId, String symbol, BigDecimal price) {
        String key = tenantId.toString() + ":" + symbol;
        currentPrices.put(key, price);
    }

    public double getVolatility(String symbol) {
        return VOLATILITY_MAP.getOrDefault(symbol, 0.025);
    }
}