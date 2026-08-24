ALTER TABLE student ADD COLUMN share_token CHAR(32) NULL;
UPDATE student SET share_token = REPLACE(UUID(), '-', '') WHERE share_token IS NULL;
ALTER TABLE student MODIFY COLUMN share_token CHAR(32) NOT NULL;
ALTER TABLE student ADD CONSTRAINT uq_student_share_token UNIQUE (share_token);
