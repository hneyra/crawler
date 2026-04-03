ALTER TABLE extracted_item ADD COLUMN content_hash VARCHAR(64);

CREATE TABLE content_change (
    id BIGSERIAL PRIMARY KEY,
    discovered_url_id BIGINT NOT NULL REFERENCES discovered_url(id),
    site_id BIGINT NOT NULL REFERENCES site(id),
    content_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64),
    properties JSONB,
    raw_snapshot_path VARCHAR(1000),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_change_url ON content_change(discovered_url_id);
CREATE INDEX idx_content_change_site ON content_change(site_id);
CREATE INDEX idx_content_change_detected_at ON content_change(detected_at);
