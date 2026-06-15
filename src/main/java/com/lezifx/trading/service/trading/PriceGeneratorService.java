package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.domain.enums.PricePathType;
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

    // ─── Sealed outcome record ────────────────────────────────────────────────

    public record SealedTradeOutcome(
        BigDecimal exitPrice,
        BigDecimal pivotPrice,  // intermediate dramatic target (null for STRAIGHT paths)
        PricePathType pathType,
        int totalTicks
    ) {}

    // ─── Seal exit + assign path ──────────────────────────────────────────────

    public SealedTradeOutcome computeSealedExitPrice(
            String symbol,
            BigDecimal entryPrice,
            int durationSeconds,
            PlatformMode mode,
            boolean isDemo,
            int activeSessionCount,
            BigDecimal houseRatio,
            long consecutiveLosses,
            boolean forceWin) {

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double volatility  = VOLATILITY_MAP.getOrDefault(symbol, 0.025);
        double timeFactor  = Math.sqrt(durationSeconds / 30.0);

        double winBias = switch (mode) {
            case WIN    -> isDemo ? 0.72 : 0.62;
            case NORMAL -> isDemo ? 0.65 : 0.47;
            case LOSE   -> isDemo ? 0.50 : 0.30;
        };

        double ratio = houseRatio.doubleValue();
        if (!isDemo) {
            if (ratio < 1.20) winBias = Math.min(winBias, 0.35);
            if (ratio < 0.50) winBias = 0.18;
        }

        double volumeAdj = Math.min(activeSessionCount / 300.0, 0.12);
        winBias = winBias - volumeAdj * (winBias - 0.47);

        // Streak protection: after 4 consecutive losses, force a win
        if (forceWin || consecutiveLosses >= 4) {
            winBias = 1.0;
        } else if (consecutiveLosses == 3) {
            winBias = Math.max(winBias, 0.72);
        } else if (consecutiveLosses == 2) {
            winBias = Math.max(winBias, 0.60);
        }

        boolean playerWins = rng.nextDouble() < winBias;
        double move        = volatility * timeFactor * (0.3 + rng.nextDouble() * 0.7);
        double multiplier  = playerWins ? (1.0 + move) : (1.0 - move);
        multiplier = Math.max(multiplier, 0.05);

        BigDecimal exitPrice = entryPrice
            .multiply(BigDecimal.valueOf(multiplier), MC)
            .setScale(6, RoundingMode.HALF_UP);

        int totalTicks = Math.max(4, durationSeconds * 2);   // 500ms per tick

        PricePathType pathType = assignPathType(rng, playerWins);
        BigDecimal pivotPrice  = computePivot(rng, pathType, entryPrice);

        return new SealedTradeOutcome(exitPrice, pivotPrice, pathType, totalTicks);
    }

    private PricePathType assignPathType(ThreadLocalRandom rng, boolean playerWins) {
        double r = rng.nextDouble();
        if (playerWins) {
            if (r < 0.60) return PricePathType.STRAIGHT_WIN;
            if (r < 0.85) return PricePathType.NEAR_MISS_WIN;
            return PricePathType.DRAMATIC_WIN;
        } else {
            if (r < 0.40) return PricePathType.STRAIGHT_LOSS;
            if (r < 0.75) return PricePathType.NEAR_MISS_LOSS;
            return PricePathType.DRAMATIC_LOSS;
        }
    }

    private BigDecimal computePivot(ThreadLocalRandom rng, PricePathType pathType, BigDecimal entry) {
        double e = entry.doubleValue();
        double pivot = switch (pathType) {
            case NEAR_MISS_WIN   -> e * (0.965 + rng.nextDouble() * 0.015);  // 2–3.5% below entry
            case DRAMATIC_WIN    -> e * (0.85  + rng.nextDouble() * 0.06);   // 9–15% below
            case NEAR_MISS_LOSS  -> e * (1.02  + rng.nextDouble() * 0.025);  // 2–4.5% above entry
            case DRAMATIC_LOSS   -> e * (1.10  + rng.nextDouble() * 0.06);   // 10–16% above
            default              -> e;
        };
        return BigDecimal.valueOf(pivot).setScale(6, RoundingMode.HALF_UP);
    }

    // ─── Tick generation with path-type drama ─────────────────────────────────

    /**
     * Generates the next price tick.
     * For two-phase paths the price travels toward pivotPrice first,
     * then crashes/rockets to sealedExitPrice in the second phase.
     */
    public BigDecimal generateBridgeTick(
            BigDecimal currentPrice,
            BigDecimal sealedExitPrice,
            BigDecimal pivotPrice,
            PricePathType pathType,
            int ticksRemaining,
            int totalTicks,
            double volatility) {

        if (ticksRemaining <= 0) return sealedExitPrice;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double current = currentPrice.doubleValue();
        double sealed  = sealedExitPrice.doubleValue();

        // Straight paths use original drift logic
        if (pathType == PricePathType.STRAIGHT_WIN || pathType == PricePathType.STRAIGHT_LOSS) {
            double drift   = (sealed - current) / ticksRemaining;
            double noise   = rng.nextGaussian() * volatility * current * 0.4;
            double next    = Math.max(current * 0.01, current + drift + noise);
            return BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP);
        }

        // Two-phase paths
        int currentTick = totalTicks - ticksRemaining;
        int phase1End   = phaseOneCutoff(pathType, totalTicks);
        boolean inPhase1 = currentTick < phase1End;

        double target;
        int ticksInPhase;

        if (inPhase1) {
            target       = (pivotPrice != null) ? pivotPrice.doubleValue() : sealed;
            ticksInPhase = Math.max(1, phase1End - currentTick);
        } else {
            target       = sealed;
            ticksInPhase = Math.max(1, ticksRemaining);
        }

        double drift = (target - current) / ticksInPhase;
        // Less noise in phase 2 so the price converges cleanly
        double noiseFactor = inPhase1 ? 0.45 : 0.20;
        double noise = rng.nextGaussian() * volatility * current * noiseFactor;
        double next  = Math.max(current * 0.01, current + drift + noise);

        return BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP);
    }

    /** How many ticks stay in phase 1 (buildup/fake-out phase). */
    private int phaseOneCutoff(PricePathType pathType, int totalTicks) {
        double fraction = switch (pathType) {
            case NEAR_MISS_LOSS -> 0.70;  // long buildup before crash
            case NEAR_MISS_WIN  -> 0.65;  // long scare before rally
            case DRAMATIC_WIN   -> 0.55;  // medium dip then long rocket
            case DRAMATIC_LOSS  -> 0.55;  // medium climb then long crash
            default             -> 0.60;
        };
        return (int)(totalTicks * fraction);
    }

    // ─── Legacy overload (idle tick broadcaster uses this) ───────────────────

    public BigDecimal generateBridgeTick(BigDecimal currentPrice, BigDecimal sealedExitPrice,
                                          int ticksRemaining, double volatility) {
        if (ticksRemaining <= 0) return sealedExitPrice;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double current = currentPrice.doubleValue();
        double sealed  = sealedExitPrice.doubleValue();
        double drift   = (sealed - current) / ticksRemaining;
        double noise   = rng.nextGaussian() * volatility * current * 0.4;
        double next    = Math.max(current * 0.01, current + drift + noise);
        return BigDecimal.valueOf(next).setScale(6, RoundingMode.HALF_UP);
    }

    // ─── Price store ──────────────────────────────────────────────────────────

    public BigDecimal getCurrentPrice(UUID tenantId, String symbol) {
        String key = tenantId.toString() + ":" + symbol;
        BigDecimal cached = currentPrices.get(key);
        if (cached != null) return cached;
        return BASE_PRICES.getOrDefault(symbol, BigDecimal.valueOf(100));
    }

    public void updateCurrentPrice(UUID tenantId, String symbol, BigDecimal price) {
        currentPrices.put(tenantId.toString() + ":" + symbol, price);
    }

    public double getVolatility(String symbol) {
        return VOLATILITY_MAP.getOrDefault(symbol, 0.025);
    }
}
