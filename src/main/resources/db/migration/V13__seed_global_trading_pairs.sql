-- V13: Seed canonical global trading pairs (tenant_id IS NULL)
-- Safe to re-run: ON CONFLICT DO NOTHING

-- Add partial unique constraint so ON CONFLICT (symbol) WHERE tenant_id IS NULL works.
-- Wrapped in exception handler so it is safe if the constraint already exists.
DO $$ BEGIN
    ALTER TABLE trading_pairs
        ADD CONSTRAINT uq_trading_pairs_symbol_global
        UNIQUE (symbol)
        WHERE tenant_id IS NULL;
EXCEPTION WHEN duplicate_table THEN
    NULL;
END $$;

INSERT INTO trading_pairs
    (id, tenant_id, symbol, name, base_asset, quote_asset, category,
     is_enabled, base_price, volatility, min_stake, max_stake,
     volatility_multiplier, allowed_durations, created_at)
VALUES
    (gen_random_uuid(), NULL, 'MAI/KES', 'Mahindi (Maize)',      'MAI', 'KES',  'kenyan', true,    65,       0.018, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'AVO/KES', 'Parachichi (Avocado)', 'AVO', 'KES',  'kenyan', true,    42,       0.032, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'CHI/KES', 'Chai (Tea Leaves)',    'CHI', 'KES',  'kenyan', true,   280,       0.022, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'UGL/KES', 'Ugali (Unga)',         'UGL', 'KES',  'kenyan', true,   130,       0.015, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'NYC/KES', 'Nyama Choma',          'NYC', 'KES',  'kenyan', true,   800,       0.028, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'MTU/KES', 'Matatu Fare',          'MTU', 'KES',  'kenyan', true,    50,       0.012, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'MBG/KES', 'Mbuzi (Goat)',         'MBG', 'KES',  'kenyan', true,  7500,      0.025, 500, 100000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'SKM/KES', 'Sukuma Wiki',          'SKM', 'KES',  'kenyan', true,    15,       0.040, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'CFE/KES', 'Kahawa (Coffee)',      'CFE', 'KES',  'kenyan', true,   510,       0.035, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'BNN/KES', 'Ndizi (Banana)',       'BNN', 'KES',  'kenyan', true,    20,       0.030, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'BTC/USDT','Bitcoin',              'BTC', 'USDT', 'crypto', true, 8700000,    0.022, 500, 500000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'ETH/USDT','Ethereum',             'ETH', 'USDT', 'crypto', true,  460000,    0.026, 500, 500000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'SOL/USDT','Solana',               'SOL', 'USDT', 'crypto', true,     145,    0.038, 100,  50000,  1.0, '{30,60,120,300}', now()),
    (gen_random_uuid(), NULL, 'BNB/USDT','BNB',                  'BNB', 'USDT', 'crypto', true,     580,    0.030, 100,  50000,  1.0, '{30,60,120,300}', now())
ON CONFLICT (symbol) WHERE tenant_id IS NULL DO NOTHING;