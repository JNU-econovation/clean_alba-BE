CREATE TABLE IF NOT EXISTS workspaces (
    workspace_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    district VARCHAR(255) NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    kakao_place_id VARCHAR(255) NULL,
    clean_score DOUBLE NULL,
    PRIMARY KEY (workspace_id),
    UNIQUE KEY uk_workspaces_kakao_place_id (kakao_place_id),
    KEY idx_workspace_clean_score (clean_score)
);

CREATE TABLE IF NOT EXISTS reviews (
    review_id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    contract_violation BIT NOT NULL,
    minimum_wage_violation BIT NOT NULL,
    weekly_allowance_violation BIT NOT NULL,
    break_time_violation BIT NOT NULL,
    wage_delay_violation BIT NOT NULL,
    schedule_change_violation BIT NOT NULL,
    substitute_coercion_violation BIT NOT NULL,
    overtime_pay_violation BIT NOT NULL,
    day_type VARCHAR(255) NULL,
    time_slot VARCHAR(255) NULL,
    coworker_count INT NULL,
    content TEXT NULL,
    status VARCHAR(255) NOT NULL,
    author_email VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (review_id),
    CONSTRAINT fk_reviews_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id),
    KEY idx_review_status_created (status, created_at, review_id),
    KEY idx_review_workspace_status_created (workspace_id, status, created_at, review_id),
    KEY idx_review_author_created (author_email, created_at, review_id)
);

CREATE TABLE IF NOT EXISTS review_attachments (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    PRIMARY KEY (attachment_id),
    CONSTRAINT fk_review_attachments_review FOREIGN KEY (review_id) REFERENCES reviews (review_id),
    KEY idx_review_attachments_review_id (review_id)
);
