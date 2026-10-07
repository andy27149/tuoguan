CREATE TABLE monthly_bill_leave_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    monthly_bill_id BIGINT NOT NULL,
    leave_date DATE NOT NULL,
    reason VARCHAR(255),
    CONSTRAINT fk_monthly_bill_leave_line_bill FOREIGN KEY (monthly_bill_id) REFERENCES monthly_bill(id) ON DELETE CASCADE
);
