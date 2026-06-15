package com.lezifx.trading.service.trading;

import com.lezifx.trading.web.dto.event.PriceTickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PriceTickBroadcastService  optimised, zero DB I/O on the hot path.
 *
 * Before:  ~10,200 DB ops/sec (full table scans + saveAll every tick).
 * After:   0 DB ops/sec during normal ticking.
 *          All state read from ActiveSessionCache (ConcurrentHashMap).
 *          PriceTick rows are NO LONGER written per tick  they were
 *          ephemeral broadcast data, not ledger data.  The cleanup
 *          scheduler is therefore also a no-op now.
 *
 * Tick interval is 500 ms (halved from 1 s)  safe because there is
 * no DB write blocking the thread anymore, giving smoother charts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceTickBroadcastService {

    private final ActiveSessionCache    activeSessionCache;
    private final PriceGeneratorService priceGeneratorService;
    private final SimpMessagingTemplate messagingTemplate;

    //  hot path  called every 500 ms by PriceTickScheduler 

    public void broadcastTicks() {
        Instant now = Instant.now();

        //  Step 1: active-session symbols (bridge ticks toward sealed exit) 
        Map<String, ActiveSessionCache.CachedSession> activePairs =
            activeSessionCache.getRepresentativeSessionsPerPair();

        for (Map.Entry<String, ActiveSessionCache.CachedSession> entry : activePairs.entrySet()) {
            ActiveSessionCache.CachedSession cs = entry.getValue();
            try {
                UUID   tenantId = cs.tenantId();
                String symbol   = cs.symbol();

                BigDecimal current = priceGeneratorService.getCurrentPrice(tenantId, symbol);

                long msRemaining = cs.expiresAt().toEpochMilli() - now.toEpochMilli();
                int  ticksLeft   = Math.max(1, (int) (msRemaining / 500));

                BigDecimal next = priceGeneratorService.generateBridgeTick(
                    current,
                    cs.sealedExitPrice(),
                    cs.pivotPrice(),
                    cs.pathType(),
                    ticksLeft,
                    cs.totalTicks(),
                    priceGeneratorService.getVolatility(symbol)
                );

                priceGeneratorService.updateCurrentPrice(tenantId, symbol, next);
                broadcast(tenantId, symbol, next, now);

            } catch (Exception e) {
                log.debug("Active tick error [{}]: {}", entry.getKey(), e.getMessage());
            }
        }

        //  Step 2: idle symbols (random walk, no DB, no save) 
        Set<String> allKeys = activeSessionCache.getAllTenantPairKeys();

        for (String pairKey : allKeys) {
            if (activePairs.containsKey(pairKey)) continue;   // already ticked above

            try {
                int colon     = pairKey.indexOf(':');
                UUID   tenantId = UUID.fromString(pairKey.substring(0, colon));
                String symbol   = pairKey.substring(colon + 1);

                double vol     = priceGeneratorService.getVolatility(symbol);
                double current = priceGeneratorService
                    .getCurrentPrice(tenantId, symbol).doubleValue();

                double change = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * vol * 0.3;
                double next   = Math.max(current * 0.5, current * (1 + change));

                BigDecimal nextPrice = BigDecimal.valueOf(next)
                    .setScale(6, RoundingMode.HALF_UP);

                priceGeneratorService.updateCurrentPrice(tenantId, symbol, nextPrice);
                broadcast(tenantId, symbol, nextPrice, now);

            } catch (Exception e) {
                log.debug("Idle tick error [{}]: {}", pairKey, e.getMessage());
            }
        }
    }

    //  private 

    private void broadcast(UUID tenantId, String symbol, BigDecimal price, Instant ts) {
        PriceTickEvent event = PriceTickEvent.builder()
            .symbol(symbol)
            .price(price)
            .timestamp(ts)
            .tenantId(tenantId)
            .build();
        messagingTemplate.convertAndSend(
            "/topic/" + tenantId + "/prices/" + symbol, event);
    }
}