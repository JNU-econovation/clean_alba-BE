INSERT INTO workspaces (workspace_id, name, address, category, district, latitude, longitude)
VALUES (10, '테스트 카페', '광주광역시 북구 테스트로 10', '카페', '테스트', 35.1000000, 126.1000000);
ALTER TABLE workspaces ALTER COLUMN workspace_id RESTART WITH 11;
