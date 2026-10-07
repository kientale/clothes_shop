-- Requested administrator credentials: login alias "admin"; email "admin@example.com".
-- The BCrypt hash was generated with the application's BCryptPasswordEncoder (cost 12).
-- Existing unrelated accounts are never overwritten or promoted.
DO $seed_admin$
DECLARE
    v_account_id UUID;
    v_role_id UUID;
    v_password_hash CONSTANT TEXT := '$2a$12$/j8XgrnEZXQVVz4Q6pxhfuYevKAsQfPqbjnKC7SYpPiFgkw2WATdK';
BEGIN
    SELECT id INTO v_role_id FROM roles WHERE code = 'ADMIN' AND deleted = FALSE;
    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'An active ADMIN role is required to seed the administrator';
    END IF;

    INSERT INTO accounts(email, password_hash, status, deleted)
    VALUES ('admin@example.com', v_password_hash, 'ACTIVE', FALSE)
    ON CONFLICT (lower(email)) DO NOTHING
    RETURNING id INTO v_account_id;

    IF v_account_id IS NULL THEN
        SELECT id INTO v_account_id FROM accounts
        WHERE lower(email) = 'admin@example.com'
          AND password_hash = v_password_hash AND status = 'ACTIVE' AND deleted = FALSE;
        IF v_account_id IS NULL THEN
            RAISE EXCEPTION 'admin@example.com is already used; existing credentials and roles were not changed';
        END IF;
    END IF;

    INSERT INTO account_roles(account_id, role_id)
    VALUES (v_account_id, v_role_id)
    ON CONFLICT (account_id, role_id) DO NOTHING;

    INSERT INTO admin_profiles(account_id, full_name)
    VALUES (v_account_id, 'Administrator')
    ON CONFLICT (account_id) DO NOTHING;
END;
$seed_admin$;
