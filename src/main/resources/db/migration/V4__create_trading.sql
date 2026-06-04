-- V4: Create trading_pairs, trade_sessions, price_ticks

CREATE TABLE trading_pairs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID REFERENCES tenants(id),
    symbol                VARCHAR(20) NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    base_asset            VARCHAR(10) NOT NULL,
    quote_asset           VARCHAR(10) NOT NULL,
    category              VARCHAR(20) NOT NULL,
    is_enabled            BOOLEAN DEFAULT TRUE,
    base_price            NUMERIC(20,6) NOT NULL,
    volatility            NUMERIC(8,6) NOT NULL,
    min_stake             NUMERIC(10,2) DEFAULT 100,
    max_stake             NUMERIC(10,2) DEFAULT 100000,
    volatility_multiplier NUMERIC(5,2) DEFAULT 1.00,
    allowed_durations     INTEGER[] DEFAULT ARRAY[30,60,120,300],
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO trading_pairs (symbol, name, base_asset, quote_asset, category, base_price, volatility) VALUES
    ('MAI/KES', 'Mahindi (Maize)',       'MAI', 'KES', 'kenyan',  65.000000,      0.018000),
    ('AVO/KES', 'Parachichi (Avocado)',  'AVO', 'KES', 'kenyan',  42.000000,      0.032000),
    ('CHI/KES', 'Chai (Tea Leaves)',     'CHI', 'KES', 'kenyan',  280.000000,     0.022000),
    ('UGL/KES', 'Ugali (Unga)',          'UGL', 'KES', 'kenyan',  130.000000,     0.015000),
    ('NYC/KES', 'Nyama Choma',           'NYC', 'KES', 'kenyan',  800.000000,     0.028000),
    ('MTU/KES', 'Matatu Fare',           'MTU', 'KES', 'kenyan',  50.000000,      0.012000),
    ('MBG/KES', 'Mbuzi (Goat)',          'MBG', 'KES', 'kenyan',  7500.000000,    0.025000),
    ('SKM/KES', 'Sukuma Wiki',           'SKM', 'KES', 'kenyan',  15.000000,      0.040000),
    ('CFE/KES', 'Kahawa (Coffee)',       'CFE', 'KES', 'kenyan',  510.000000,     0.035000),
    ('BNN/KES', 'Ndizi (Banana)',        'BNN', 'KES', 'kenyan',  20.000000,      0.030000),
    ('BTC/USDT','Bitcoin',               'BTC', 'USDT','crypto',  8700000.000000, 0.022000),
    ('ETH/USDT','Ethereum',              'ETH', 'USDT','crypto',  460000.000000,  0.026000),
    ('SOL/USDT','Solana',                'SOL', 'USDT','crypto',  145.000000,     0.038000),
    ('BNB/USDT','BNB',                   'BNB', 'USDT','crypto',  580.000000,     0.030000);

CREATE TABLE trade_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    user_id             UUID NOT NULL REFERENCES users(id),
    pair_symbol         VARCHAR(20) NOT NULL,
    is_demo             BOOLEAN NOT NULL DEFAULT FALSE,
    is_marketer_trade   BOOLEAN NOT NULL DEFAULT FALSE,
    stake_amount        NUMERIC(15,2) NOT NULL,
    entry_price         NUMERIC(20,6) NOT NULL,
    sealed_exit_price   NUMERIC(20,6) NOT NULL,
    locked_payout_rate  NUMERIC(5,4) NOT NULL,
    duration_seconds    INT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    outcome             VARCHAR(10),
    profit_amount       NUMERIC(15,2),
    actual_exit_price   NUMERIC(20,6),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    settled_at          TIMESTAMPTZ,
    CONSTRAINT stake_positive CHECK (stake_amount > 0),
    CONSTRAINT payout_rate_valid CHECK (locked_payout_rate BETWEEN 0.50 AND 0.99)
);

CREATE TABLE price_ticks (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    symbol    VARCHAR(20) NOT NULL,
    price     NUMERIC(20,6) NOT NULL,
    tick_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trading_pairs_tenant   ON trading_pairs(tenant_id);
CREATE INDEX idx_trading_pairs_global   ON trading_pairs(symbol) WHERE tenant_id IS NULL;
CREATE INDEX idx_trade_sessions_tenant  ON trade_sessions(tenant_id);
CREATE INDEX idx_trade_sessions_user    ON trade_sessions(user_id);
CREATE INDEX idx_trade_sessions_active  ON trade_sessions(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_trade_sessions_status  ON trade_sessions(tenant_id, status);
CREATE INDEX idx_trade_sessions_marketer ON trade_sessions(tenant_id, is_marketer_trade);
CREATE INDEX idx_price_ticks_symbol     ON price_ticks(tenant_id, symbol, tick_time DESC);