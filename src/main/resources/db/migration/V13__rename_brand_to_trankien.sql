-- Keep earlier migrations unchanged so existing Flyway checksums remain valid.
-- The helper exists only for this database session.
CREATE FUNCTION pg_temp.trankien_brand(value TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT replace(replace(replace(value, 'LemonadeX', 'TranKien'),
                           'lemonadex', 'trankien'), 'LEMONADEX', 'TRANKIEN')
$$;

UPDATE system_settings
SET setting_value = pg_temp.trankien_brand(setting_value), revision = revision + 1
WHERE setting_group = 'STORE' AND setting_key = 'CONFIG'
  AND setting_value ~* 'lemonadex';

UPDATE payment_methods
SET configuration = pg_temp.trankien_brand(configuration::text)::jsonb, revision = revision + 1
WHERE configuration::text ~* 'lemonadex';

-- Reuse the existing brand ID so products and the repeatable seed keep their relationships.
UPDATE brands
SET name = pg_temp.trankien_brand(name), slug = pg_temp.trankien_brand(slug),
    description = pg_temp.trankien_brand(description)
WHERE name ~* 'lemonadex' OR slug ~* 'lemonadex' OR description ~* 'lemonadex';

UPDATE banners
SET title = pg_temp.trankien_brand(title)
WHERE title ~* 'lemonadex';

UPDATE articles
SET title = pg_temp.trankien_brand(title), content = pg_temp.trankien_brand(content)
WHERE title ~* 'lemonadex' OR content ~* 'lemonadex';

UPDATE store_policies
SET title = pg_temp.trankien_brand(title), content = pg_temp.trankien_brand(content)
WHERE title ~* 'lemonadex' OR content ~* 'lemonadex';
