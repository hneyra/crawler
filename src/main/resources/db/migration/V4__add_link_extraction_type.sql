ALTER TABLE category ADD COLUMN link_extraction_type VARCHAR(50) NOT NULL DEFAULT 'HREF';
ALTER TABLE category ADD COLUMN link_attribute VARCHAR(255);
