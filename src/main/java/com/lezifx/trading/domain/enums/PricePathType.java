package com.lezifx.trading.domain.enums;

public enum PricePathType {
    STRAIGHT_WIN,    // steady drift up to exit
    NEAR_MISS_WIN,   // drops near entry then spikes to win
    DRAMATIC_WIN,    // V-shape: deep dip then rockets to win
    STRAIGHT_LOSS,   // steady drift down to exit
    NEAR_MISS_LOSS,  // climbs near entry then crashes to loss
    DRAMATIC_LOSS    // inverted-V: climbs high then collapses to loss
}
