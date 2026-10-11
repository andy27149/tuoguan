ALTER TABLE institution
    ADD COLUMN contact_name VARCHAR(100) NULL,
    ADD COLUMN contact_phone VARCHAR(20) NULL,
    ADD COLUMN address VARCHAR(255) NULL,
    ADD COLUMN valid_until DATE NULL;
