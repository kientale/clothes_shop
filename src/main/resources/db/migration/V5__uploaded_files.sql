-- Uploaded files (avatars for now) stored in PostgreSQL, so they survive redeploys of a
-- stateless application server and need no extra storage service.
CREATE TABLE uploaded_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose VARCHAR(30) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes INTEGER NOT NULL,
    data BYTEA NOT NULL,
    uploaded_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_uploaded_files_size CHECK (size_bytes > 0 AND size_bytes = octet_length(data)),
    CONSTRAINT fk_uploaded_files_uploader FOREIGN KEY (uploaded_by) REFERENCES accounts(id) ON DELETE SET NULL
);

CREATE TRIGGER trg_uploaded_files_updated_at BEFORE UPDATE ON uploaded_files
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
