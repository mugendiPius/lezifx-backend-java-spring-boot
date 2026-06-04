package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.trading.PriceTick;
import com.lezifx.trading.domain.trading.TradeSession;
import com.lezifx.trading.repository.PriceTickRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.web.dto.event.PriceTickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceTickBroadcastService {

    private final TradeSessionRepository tradeSessionRepository;
    private final PriceGeneratorService priceGeneratorService;
    private final PriceTickRepository priceTickRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantRepository tenantRepository;
    private final TradingPairRepository tradingPairRepository;

    private final Map<String, BigDecimal> lastBroadcastPrice = new ConcurrentHashMap<>();

    @Transactional
    public void broadcastTicks() {
        Instant now = Instant.now();

        // Step 1: Get all ACTIVE sessions
        List<TradeSession> activeSessions = tradeSessionRepository
            .findAllByStatus(TradeSessionStatus.ACTIVE);

        // Step 2: Group by tenantId:symbol
        Map<String, List<TradeSession>> grouped = activeSessions.stream()
            .collect(Collectors.groupingBy(
                s -> s.getTenant().getId().toString() + ":" + s.getPairSymbol()
            ));

        // Track which symbols were ticked this cycle (for idle fill)
        Set<String> tickedSymbols = new HashSet<>();
        List<PriceTick> ticksToSave = new ArrayList<>();

        // Step 3: Generate one tick per group
        for (Map.Entry<String, List<TradeSession>> entry : grouped.entrySet()) {
            try {
                List<TradeSession> sessions = entry.getValue();
                TradeSession representative = sessions.get(0);
                UUID tenantId = representative.getTenant().getId();
                String symbol = representative.getPairSymbol();

                BigDecimal currentPrice = priceGeneratorService.getCurrentPrice(tenantId, symbol);

                long ticksRemainingMs = representative.getExpiresAt().toEpochMilli() - now.toEpochMilli();
                int ticksRemaining = Math.max(1, (int) (ticksRemainingMs / 1000));

                double volatility = priceGeneratorService.getVolatility(symbol);

                BigDecimal newPrice = priceGeneratorService.generateBridgeTick(
                    currentPrice,
                    representative.getSealedExitPrice(),
                    ticksRemaining,
                    volatility
                );

                priceGeneratorService.updateCurrentPrice(tenantId, symbol, newPrice);

                PriceTick priceTick = PriceTick.builder()
                    .tenantId(tenantId)
                    .symbol(symbol)
                    .price(newPrice)
                    .build();
                ticksToSave.add(priceTick);

                PriceTickEvent event = PriceTickEvent.builder()
                    .symbol(symbol)
                    .price(newPrice)
                    .timestamp(now)
                    .tenantId(tenantId)
                    .build();

                messagingTemplate.convertAndSend(
                    "/topic/" + tenantId + "/prices/" + symbol, event);

                tickedSymbols.add(tenantId.toString() + ":" + symbol);

            } catch (Exception e) {
                log.debug("Tick error for group {}: {}", entry.getKey(), e.getMessage());
            }
        }

        // Batch save all active-session ticks
        if (!ticksToSave.isEmpty()) {
            try {
                priceTickRepository.saveAll(ticksToSave);
            } catch (Exception e) {
                log.debug("Batch tick save error: {}", e.getMessage());
            }
        }

        // Step 4: Tick idle symbols (no active sessions) — no DB save
        try {
            var globalPairs = tradingPairRepository.findByTenantIdIsNullAndIsEnabledTrue();
            var activeTenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);

            for (var tenant : activeTenants) {
                UUID tenantId = tenant.getId();
                for (var pair : globalPairs) {
                    String key = tenantId.toString() + ":" + pair.getSymbol();
                    if (tickedSymbols.contains(key)) continue;

                    try {
                        double vol = priceGeneratorService.getVolatility(pair.getSymbol());
                        double current = priceGeneratorService
                            .getCurrentPrice(tenantId, pair.getSymbol())
                            .doubleValue();

                        double change = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * vol;
                        double next = current * (1 + change * 0.3);
                        next = Math.max(next, current * 0.5);

                        BigDecimal newPrice = BigDecimal.valueOf(next)
                            .setScale(6, RoundingMode.HALF_UP);

                        priceGeneratorService.updateCurrentPrice(tenantId, pair.getSymbol(), newPrice);

                        PriceTickEvent event = PriceTickEvent.builder()
                            .symbol(pair.getSymbol())
                            .price(newPrice)
                            .timestamp(now)
                            .tenantId(tenantId)
                            .build();

                        messagingTemplate.convertAndSend(
                            "/topic/" + tenantId + "/prices/" + pair.getSymbol(), event);

                    } catch (Exception e) {
                        log.debug("Idle tick error for {}:{}: {}", tenantId, pair.getSymbol(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Idle tick cycle error: {}", e.getMessage());
        }
    }
}