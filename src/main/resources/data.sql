-- 임시 시드 데이터 (로컬 H2 임베디드 DB에서만 자동 실행됨).
-- 운영 MySQL에는 spring.sql.init.mode 기본값(embedded) 때문에 실행되지 않음.
-- workspace_id / review_id는 IDENTITY 자동 생성에 맡김(빈 테이블 기준 1부터 순서대로 부여됨).
-- clean_score는 시드하지 않는다: 앱 기동 시 CleanScoreInitializer가 승인된 리뷰로부터 계산해 채운다.
INSERT INTO workspaces (name, address, category, district, latitude, longitude) VALUES
('스타벅스 전남대후문점', '광주 북구 용봉로 105', '카페', '후문', 35.1812000, 126.9088000),   -- id 1
('메가MGC커피 상대점', '광주 북구 용봉동 1097-3', '카페', '상대', 35.1768000, 126.9043000),  -- id 2
('GS25 용봉정문점', '광주 북구 용봉로 77', '편의점', '정문', 35.1761000, 126.9072000),        -- id 3
('한솥도시락 전남대점', '광주 북구 용봉로 110', '식당', '후문', 35.1805000, 126.9095000),     -- id 4
('투썸플레이스 예대점', '광주 북구 용봉동 1089', '카페', '예대', 35.1779000, 126.9120000),    -- id 5
('김밥천국 상대점', '광주 북구 용봉동 1095-2', '식당', '상대', 35.1771000, 126.9038000),      -- id 6
('청년다방 후문점', '광주 북구 용봉로 108', '카페', '후문', 35.1808000, 126.9082000),         -- id 7
('봉구스밥버거 정문점', '광주 북구 용봉로 80', '식당', '정문', 35.1758000, 126.9068000),      -- id 8
('공차 전남대점', '광주 북구 용봉동 1100', '카페', '상대', 35.1765000, 126.9050000),          -- id 9
('용봉호프 후문점', '광주 북구 용봉로 112', '주점', '후문', 35.1815000, 126.9100000);         -- id 10 (리뷰 없음 → null → 미노출)

-- 리뷰 시드: 위반 항목 순서 = 근로계약서/최저임금/주휴수당/휴게시간/급여지연/스케줄변경/대타강요/초과근무
-- 리뷰 1건 점수 = 100 - (위반 수 × 12.5). 사업장 클린지수 = 승인(APPROVED) 리뷰 평균.
INSERT INTO reviews
(workspace_id, contract_violation, minimum_wage_violation, weekly_allowance_violation, break_time_violation, wage_delay_violation, schedule_change_violation, substitute_coercion_violation, overtime_pay_violation, coworker_count, content, status, author_email, created_at) VALUES
-- W1 스타벅스: (100 + 87.5) / 2 = 93.75 → 94 (우수)
(1, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 3, '분위기 좋고 사장님 친절해요.', 'APPROVED', 'seed1@kakao.com', CURRENT_TIMESTAMP),
(1, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 2, '계약서를 안 썼던 점만 아쉬워요.', 'APPROVED', 'seed2@kakao.com', CURRENT_TIMESTAMP),
-- W1 검수 대기 리뷰(위반 7개, 12.5점): PENDING이라 집계에서 제외되어야 함(점수 안 떨어짐)
(1, FALSE, TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  1, '검수 대기 중 후기입니다.', 'PENDING', 'seed3@kakao.com', CURRENT_TIMESTAMP),
-- W2 메가커피: 87.5 → 88 (우수)
(2, FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, 2, '휴게시간이 조금 부족했어요.', 'APPROVED', 'seed4@kakao.com', CURRENT_TIMESTAMP),
-- W3 GS25: (87.5 + 75) / 2 = 81.25 → 81 (우수)
(3, TRUE,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 4, '계약서만 안 썼어요.', 'APPROVED', 'seed5@kakao.com', CURRENT_TIMESTAMP),
(3, TRUE,  FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, FALSE, 3, '계약서·휴게시간이 아쉬웠습니다.', 'APPROVED', 'seed6@kakao.com', CURRENT_TIMESTAMP),
-- W4 한솥: 75 → 75 (보통)
(4, FALSE, FALSE, TRUE,  TRUE,  FALSE, FALSE, FALSE, FALSE, 2, '주휴수당과 휴게시간 문제.', 'APPROVED', 'seed7@kakao.com', CURRENT_TIMESTAMP),
-- W5 투썸: (75 + 62.5) / 2 = 68.75 → 69 (보통)
(5, FALSE, FALSE, FALSE, TRUE,  FALSE, FALSE, FALSE, TRUE,  3, '초과근무 수당이 안 나왔어요.', 'APPROVED', 'seed8@kakao.com', CURRENT_TIMESTAMP),
(5, FALSE, TRUE,  FALSE, TRUE,  FALSE, FALSE, FALSE, TRUE,  2, '최저임금·초과수당 문제.', 'APPROVED', 'seed9@kakao.com', CURRENT_TIMESTAMP),
-- W6 김밥천국: 62.5 → 63 (보통)
(6, FALSE, FALSE, TRUE,  FALSE, TRUE,  TRUE,  FALSE, FALSE, 2, '급여 지연과 일방 스케줄 변경.', 'APPROVED', 'seed10@kakao.com', CURRENT_TIMESTAMP),
-- W7 청년다방: (62.5 + 37.5) / 2 = 50 → 50 (주의)
(7, FALSE, FALSE, FALSE, TRUE,  FALSE, TRUE,  TRUE,  FALSE, 1, '대타 강요가 잦았어요.', 'APPROVED', 'seed11@kakao.com', CURRENT_TIMESTAMP),
(7, TRUE,  TRUE,  TRUE,  FALSE, TRUE,  FALSE, FALSE, TRUE,  1, '전반적으로 문제가 많았습니다.', 'APPROVED', 'seed12@kakao.com', CURRENT_TIMESTAMP),
-- W8 봉구스: 50 → 50 (주의)
(8, FALSE, TRUE,  TRUE,  FALSE, TRUE,  FALSE, FALSE, TRUE,  1, '임금 관련 문제가 많았어요.', 'APPROVED', 'seed13@kakao.com', CURRENT_TIMESTAMP),
-- W9 공차: 25 → 25 (위험)
(9, TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  FALSE, FALSE, TRUE,  1, '거의 모든 항목이 지켜지지 않았어요.', 'APPROVED', 'seed14@kakao.com', CURRENT_TIMESTAMP);
-- W10 용봉호프: 승인 리뷰 없음 → clean_score null → 지도·목록 미노출
