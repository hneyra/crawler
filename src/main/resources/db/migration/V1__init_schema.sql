CREATE TABLE site (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    base_url        VARCHAR(2048)   NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    politeness_delay_ms INTEGER     NOT NULL DEFAULT 1000,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE category (
    id                  BIGSERIAL       PRIMARY KEY,
    site_id             BIGINT          NOT NULL REFERENCES site(id),
    name                VARCHAR(255)    NOT NULL,
    url                 VARCHAR(2048)   NOT NULL,
    pagination_type     VARCHAR(50)     NOT NULL,
    item_link_selector  VARCHAR(500)    NOT NULL,
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE TABLE discovered_url (
    id              BIGSERIAL       PRIMARY KEY,
    category_id     BIGINT          NOT NULL REFERENCES category(id),
    url             VARCHAR(2048)   NOT NULL,
    url_hash        VARCHAR(64)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_discovered_url_hash ON discovered_url(url_hash);
CREATE INDEX idx_discovered_url_status ON discovered_url(status);

CREATE TABLE extracted_item (
    id                  BIGSERIAL       PRIMARY KEY,
    discovered_url_id   BIGINT          NOT NULL REFERENCES discovered_url(id),
    site_id             BIGINT          NOT NULL REFERENCES site(id),
    title               VARCHAR(1000)   NOT NULL,
    properties          JSONB,
    raw_snapshot_path   VARCHAR(1000),
    extracted_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
