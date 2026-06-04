package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.SocialAction;
import com.lezifx.trading.domain.enums.SocialEventType;
import com.lezifx.trading.domain.trading.SocialFeedEvent;
import com.lezifx.trading.domain.trading.TradeSession;
import com.lezifx.trading.repository.SocialFeedEventRepository;
import com.lezifx.trading.web.dto.response.SocialFeedEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialFeedService {

    private static final String[] KENYAN_NAMES = {
        "Wanjiku", "Kamau", "Achieng", "Otieno", "Nekesa",
        "Kipchoge", "Muthoni", "Njoroge", "Anyango", "Kimani",
        "Adhiambo", "Muriuki", "Awino", "Karanja", "Moraa"
    };

    private static final SocialAction[] ACTIONS = {
        SocialAction.won, SocialAction.staked, SocialAction.cashed
    };

    private final SimpMessagingTemplate messagingTemplate;
    private final SocialFeedEventRepository socialFeedEventRepository;

    @Transactional
    public void broadcastRealWin(TradeSession session) {
        SocialEventType eventType = Boolean.TRUE.equals(session.getIsMarketerTrade())
            ? SocialEventType.MARKETER_WIN
            : SocialEventType.REAL_WIN;

        String displayName = session.getUser().getFullName() != null
            ? session.getUser().getFullName().split(" ")[0]
            : "Player";

        SocialFeedEvent event = SocialFeedEvent.builder()
            .tenant(session.getTenant())
            .eventType(eventType)
            .displayName(displayName)
            .action(SocialAction.won)
            .amount(session.getProfitAmount())
            .pairSymbol(session.getPairSymbol())
            .isSimulated(false)
            .sourceTradeSessionId(session.getId())
            .build();

        event = socialFeedEventRepository.save(event);
        broadcastToTopic(session.getTenant().getId(), event);
    }

    @Transactional
    public void generateAndBroadcastSimulated(UUID tenantId, String pairSymbol) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        String name = KENYAN_NAMES[rng.nextInt(KENYAN_NAMES.length)];
        BigDecimal amount = BigDecimal.valueOf(200 + rng.nextInt(14801))
            .setScale(2, RoundingMode.HALF_UP);
        SocialAction action = ACTIONS[rng.nextInt(ACTIONS.length)];

        com.lezifx.trading.domain.tenant.Tenant tenantRef =
            new com.lezifx.trading.domain.tenant.Tenant();
        tenantRef.setId(tenantId);

        // Use a proxy-safe tenant reference via a findById approach
        // We construct a minimal entity here; the repository save will use the FK
        SocialFeedEvent event = SocialFeedEvent.builder()
            .eventType(SocialEventType.SIMULATED)
            .displayName(name)
            .action(action)
            .amount(amount)
            .pairSymbol(pairSymbol)
            .isSimulated(true)
            .build();

        // Set tenant ID by reflection-safe approach — set the proxy tenant
        event.setTenant(tenantRef);
        event = socialFeedEventRepository.save(event);
        broadcastToTopic(tenantId, event);
    }

    @Transactional(readOnly = true)
    public List<SocialFeedEventDto> getRecentFeed(UUID tenantId) {
        return socialFeedEventRepository
            .findTop20ByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    private void broadcastToTopic(UUID tenantId, SocialFeedEvent event) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/" + tenantId + "/social",
                toDto(event)
            );
        } catch (Exception e) {
            log.warn("Could not broadcast social feed event for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private SocialFeedEventDto toDto(SocialFeedEvent event) {
        return SocialFeedEventDto.builder()
            .id(event.getId())
            .eventType(event.getEventType().name())
            .displayName(event.getDisplayName())
            .action(event.getAction().name())
            .amount(event.getAmount())
            .pairSymbol(event.getPairSymbol())
            .isSimulated(Boolean.TRUE.equals(event.getIsSimulated()))
            .createdAt(event.getCreatedAt())
            .build();
    }
}