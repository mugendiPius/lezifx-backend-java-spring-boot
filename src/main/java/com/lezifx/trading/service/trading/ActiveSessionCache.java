package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.PricePathType;
import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.trading.TradeSession;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero-DB-read cache for the price tick hot path.
 *
 * HOT PATH  (every 500 ms  1 s): getRepresentativeSessionsPerPair(), getAllTenantPairKeys()
 *    pure ConcurrentHashMap reads, no DB, no locks.
 *
 * WARM PATH (on buy / settle + 5-min safety refresh):
 *   onSessionCreated(), onSessionExpired(), scheduledRefresh()
 *    single map mutation or 2 DB reads max.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActiveSessionCache {

    //  internal record
    public record CachedSession(
        UUID         sessionId,
        UUID         tenantId,
        String       symbol,
        BigDecimal   sealedExitPrice,
        BigDecimal   pivotPrice,
        PricePathType pathType,
        int          totalTicks,
        Instant      expiresAt
    ) {}

    // key = "tenantId:symbol:sessionId"
    private final ConcurrentHashMap<String, CachedSession> sessions =
        new ConcurrentHashMap<>(512);

    // key = "tenantId:symbol"  set of full session keys
    private final ConcurrentHashMap<String, Set<String>> pairIndex =
        new ConcurrentHashMap<>(256);

    // All "tenantId:symbol" pairs for enabled global pairs  active tenants.
    // Refreshed every 5 min  never per-tick.
    private volatile Set<String> allTenantPairKeys = Collections.emptySet();

    private final TradeSessionRepository tradeSessionRepository;
    private final TradingPairRepository  tradingPairRepository;
    private final TenantRepository       tenantRepository;

    //  startup 

    @PostConstruct
    public void init() {
        rebuildSessionCache();
        refreshTenantPairs();
        log.info("ActiveSessionCache initialised: {} active sessions, {} tenant-pair keys",
            sessions.size(), allTenantPairKeys.size());
    }

    //  5-minute safety net refresh 

    @Scheduled(fixedDelay = 300_000)
    @Transactional(readOnly = true)
    public void scheduledRefresh() {
        rebuildSessionCache();
        refreshTenantPairs();
    }

    //  event-driven mutations 

    public void onSessionCreated(TradeSession session) {
        String sk = sessionKey(session.getTenant().getId(),
                               session.getPairSymbol(), session.getId());
        String pk = pairKey(session.getTenant().getId(), session.getPairSymbol());

        PricePathType pathType = session.getPathType() != null
            ? PricePathType.valueOf(session.getPathType())
            : PricePathType.STRAIGHT_WIN;

        int totalTicks = session.getDurationSeconds() != null
            ? session.getDurationSeconds() * 2 : 60;

        sessions.put(sk, new CachedSession(
            session.getId(),
            session.getTenant().getId(),
            session.getPairSymbol(),
            session.getSealedExitPrice(),
            session.getPivotPrice(),
            pathType,
            totalTicks,
            session.getExpiresAt()
        ));
        pairIndex.computeIfAbsent(pk, k -> ConcurrentHashMap.newKeySet()).add(sk);
    }

    public void onSessionExpired(UUID tenantId, String symbol, UUID sessionId) {
        String sk = sessionKey(tenantId, symbol, sessionId);
        String pk = pairKey(tenantId, symbol);
        sessions.remove(sk);
        Set<String> idx = pairIndex.get(pk);
        if (idx != null) {
            idx.remove(sk);
            if (idx.isEmpty()) pairIndex.remove(pk);
        }
    }

    //  hot-path reads 

    /**
     * Returns one representative CachedSession per tenantId:symbol.
     * Skips lazily-stale entries (settlement scheduler removes them properly).
     */
    public Map<String, CachedSession> getRepresentativeSessionsPerPair() {
        Map<String, CachedSession> result = new HashMap<>(pairIndex.size() * 2);
        Instant now = Instant.now();
        for (Map.Entry<String, Set<String>> e : pairIndex.entrySet()) {
            for (String sk : e.getValue()) {
                CachedSession cs = sessions.get(sk);
                if (cs == null || cs.expiresAt().isBefore(now)) continue;
                result.putIfAbsent(e.getKey(), cs);
                break;
            }
        }
        return result;
    }

    /** All tenantId:symbol keys for idle broadcasting. */
    public Set<String> getAllTenantPairKeys() {
        return allTenantPairKeys;
    }

    //  private helpers 

    @Transactional(readOnly = true)
    public void rebuildSessionCache() {
        try {
            List<TradeSession> active = tradeSessionRepository
                .findAllByStatus(TradeSessionStatus.ACTIVE);

            ConcurrentHashMap<String, CachedSession> newSessions = new ConcurrentHashMap<>(active.size() * 2);
            ConcurrentHashMap<String, Set<String>>   newIndex    = new ConcurrentHashMap<>(active.size());

            for (TradeSession s : active) {
                String sk = sessionKey(s.getTenant().getId(), s.getPairSymbol(), s.getId());
                String pk = pairKey(s.getTenant().getId(), s.getPairSymbol());
                PricePathType pt = s.getPathType() != null
                    ? PricePathType.valueOf(s.getPathType())
                    : PricePathType.STRAIGHT_WIN;
                int tt = s.getDurationSeconds() != null ? s.getDurationSeconds() * 2 : 60;
                CachedSession cs = new CachedSession(
                    s.getId(), s.getTenant().getId(),
                    s.getPairSymbol(), s.getSealedExitPrice(),
                    s.getPivotPrice(), pt, tt, s.getExpiresAt()
                );
                newSessions.put(sk, cs);
                newIndex.computeIfAbsent(pk, k -> ConcurrentHashMap.newKeySet()).add(sk);
            }

            sessions.clear();
            sessions.putAll(newSessions);
            pairIndex.clear();
            pairIndex.putAll(newIndex);

            log.debug("Session cache rebuilt: {} active sessions", active.size());
        } catch (Exception e) {
            log.warn("Session cache rebuild failed: {}", e.getMessage());
        }
    }

    private void refreshTenantPairs() {
        try {
            var globalPairs   = tradingPairRepository.findByTenantIdIsNullAndIsEnabledTrue();
            var activeTenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);

            Set<String> keys = new HashSet<>(globalPairs.size() * activeTenants.size() * 2);
            for (var tenant : activeTenants) {
                for (var pair : globalPairs) {
                    keys.add(pairKey(tenant.getId(), pair.getSymbol()));
                }
            }
            allTenantPairKeys = Collections.unmodifiableSet(keys);
            log.debug("Tenant-pair keys refreshed: {} keys", keys.size());
        } catch (Exception e) {
            log.warn("Tenant-pair refresh failed: {}", e.getMessage());
        }
    }

    private static String sessionKey(UUID tenantId, String symbol, UUID sessionId) {
        return tenantId + ":" + symbol + ":" + sessionId;
    }

    public static String pairKey(UUID tenantId, String symbol) {
        return tenantId + ":" + symbol;
    }
}