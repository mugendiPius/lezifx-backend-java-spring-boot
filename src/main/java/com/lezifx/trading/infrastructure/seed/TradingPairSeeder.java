package com.lezifx.trading.infrastructure.seed;

import com.lezifx.trading.domain.trading.TradingPair;
import com.lezifx.trading.repository.TradingPairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the 14 canonical global trading pairs on every startup.
 *
 * Strategy: pure JPA  no native SQL, no Flyway, no schema constraints required.
 * For each pair we check whether a global row (tenant_id IS NULL) with that symbol
 * already exists; if not we save it. Completely idempotent and re-run safe.
 *
 * @Order(10) ensures this runs after tenant/auth bootstrap seeders.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class TradingPairSeeder implements ApplicationRunner {

    private static final int[] DEFAULT_DURATIONS = {30, 60, 120, 300};

    private final TradingPairRepository tradingPairRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<SeedPair> pairs = List.of(
                new SeedPair("MAI/KES", "Mahindi (Maize)",      "MAI", "KES",  "kenyan",    65,       0.018, 100,  50000),
                new SeedPair("AVO/KES", "Parachichi (Avocado)", "AVO", "KES",  "kenyan",    42,       0.032, 100,  50000),
                new SeedPair("CHI/KES", "Chai (Tea Leaves)",    "CHI", "KES",  "kenyan",   280,       0.022, 100,  50000),
                new SeedPair("UGL/KES", "Ugali (Unga)",         "UGL", "KES",  "kenyan",   130,       0.015, 100,  50000),
                new SeedPair("NYC/KES", "Nyama Choma",          "NYC", "KES",  "kenyan",   800,       0.028, 100,  50000),
                new SeedPair("MTU/KES", "Matatu Fare",          "MTU", "KES",  "kenyan",    50,       0.012, 100,  50000),
                new SeedPair("MBG/KES", "Mbuzi (Goat)",         "MBG", "KES",  "kenyan",  7500,      0.025, 500, 100000),
                new SeedPair("SKM/KES", "Sukuma Wiki",          "SKM", "KES",  "kenyan",    15,       0.040, 100,  50000),
                new SeedPair("CFE/KES", "Kahawa (Coffee)",      "CFE", "KES",  "kenyan",   510,       0.035, 100,  50000),
                new SeedPair("BNN/KES", "Ndizi (Banana)",       "BNN", "KES",  "kenyan",    20,       0.030, 100,  50000),
                new SeedPair("BTC/USDT","Bitcoin",              "BTC", "USDT", "crypto", 8700000,    0.022, 500, 500000),
                new SeedPair("ETH/USDT","Ethereum",             "ETH", "USDT", "crypto",  460000,    0.026, 500, 500000),
                new SeedPair("SOL/USDT","Solana",               "SOL", "USDT", "crypto",     145,    0.038, 100,  50000),
                new SeedPair("BNB/USDT","BNB",                  "BNB", "USDT", "crypto",     580,    0.030, 100,  50000)
        );

        int inserted = 0;
        for (SeedPair p : pairs) {
            boolean exists = tradingPairRepository
                    .findBySymbolAndTenantIdIsNull(p.symbol)
                    .isPresent();
            if (!exists) {
                tradingPairRepository.save(TradingPair.builder()
                        .tenantId(null)
                        .symbol(p.symbol)
                        .name(p.name)
                        .baseAsset(p.baseAsset)
                        .quoteAsset(p.quoteAsset)
                        .category(p.category)
                        .isEnabled(true)
                        .basePrice(BigDecimal.valueOf(p.basePrice))
                        .volatility(BigDecimal.valueOf(p.volatility))
                        .minStake(BigDecimal.valueOf(p.minStake))
                        .maxStake(BigDecimal.valueOf(p.maxStake))
                        .volatilityMultiplier(BigDecimal.ONE)
                        .allowedDurations(DEFAULT_DURATIONS)
                        .build());
                inserted++;
            }
        }

        if (inserted > 0) {
            log.info("TradingPairSeeder: inserted {} new global trading pair(s)", inserted);
        } else {
            log.info("TradingPairSeeder: all {} global trading pairs already present", pairs.size());
        }
    }

    /** Lightweight value holder  avoids a 14-row constructor soup above. */
    private record SeedPair(
            String symbol, String name,
            String baseAsset, String quoteAsset, String category,
            double basePrice, double volatility,
            double minStake, double maxStake
    ) {}
}