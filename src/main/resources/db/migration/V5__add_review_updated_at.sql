-- 리뷰 수정 시각 컬럼 추가. 기존 리뷰는 작성 시각(created_at)으로 초기화한다.
ALTER TABLE reviews ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE reviews SET updated_at = created_at;

ALTER TABLE reviews MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
