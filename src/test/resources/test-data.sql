INSERT INTO workspaces (workspace_id, name, address, category, district, latitude, longitude)
VALUES (10, '테스트 카페', '광주광역시 북구 테스트로 10', '카페', '테스트', 35.1000000, 126.1000000);

INSERT INTO workspaces (workspace_id, name, address, category, district, latitude, longitude, clean_score)
VALUES
    (11, '탑독PC', '전남광주통합특별시 북구 우치로 128 3층', 'PC방', '전대후문', 35.1788460, 126.9123310, 100.0000000),
    (12, '디저트39 전대점', '전남광주통합특별시 북구 호동로 12-8 1 2층', '카페', '전대후문', 35.1744670, 126.9142980, 93.7500000),
    (13, '더벤티 전남대', '전남광주통합특별시 북구 우치로 120 1층', '카페', '전대후문', 35.1782980, 126.9123570, 93.7500000),
    (14, '파스쿠찌 전남대점', '전남광주통합특별시 북구 호동로 15 1~4층', '카페', '전대후문', 35.1751910, 126.9142310, 91.6666667);

INSERT INTO reviews (
    review_id, workspace_id,
    contract_violation, minimum_wage_violation, weekly_allowance_violation, break_time_violation,
    wage_delay_violation, schedule_change_violation, substitute_coercion_violation, overtime_pay_violation,
    coworker_count, content, status, author_email, created_at, updated_at
)
VALUES
    (1, 11, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 3, '근무 환경이 전반적으로 안정적이었습니다.', 'APPROVED', 'seed-topdog@example.com', '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
    (2, 12, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 2, '업무 안내가 명확하고 동료들과 협업하기 좋았습니다.', 'APPROVED', 'seed-dessert39-1@example.com', '2026-07-02 10:00:00', '2026-07-02 10:00:00'),
    (3, 12, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, 2, '바쁜 시간에는 휴게시간 조율이 필요했습니다.', 'APPROVED', 'seed-dessert39-2@example.com', '2026-07-03 10:00:00', '2026-07-03 10:00:00'),
    (4, 13, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 2, '처음 근무할 때 업무 교육을 친절하게 받았습니다.', 'APPROVED', 'seed-theventi-1@example.com', '2026-07-04 10:00:00', '2026-07-04 10:00:00'),
    (5, 13, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, 3, '일정 변경은 미리 공유되면 더 좋겠습니다.', 'APPROVED', 'seed-theventi-2@example.com', '2026-07-05 10:00:00', '2026-07-05 10:00:00'),
    (6, 13, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, 2, '급여 일정 안내를 조금 더 구체적으로 받으면 좋겠습니다.', 'APPROVED', 'seed-theventi-3@example.com', '2026-07-06 10:00:00', '2026-07-06 10:00:00'),
    (7, 13, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 3, '매장 분위기가 차분하고 업무 분담이 잘 되어 있었습니다.', 'APPROVED', 'seed-theventi-4@example.com', '2026-07-07 10:00:00', '2026-07-07 10:00:00'),
    (8, 14, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 4, '근무 절차와 담당 업무가 명확하게 안내되었습니다.', 'APPROVED', 'seed-pascucci-1@example.com', '2026-07-08 10:00:00', '2026-07-08 10:00:00'),
    (9, 14, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 3, '동료 간 소통이 원활해 업무를 배우기 좋았습니다.', 'APPROVED', 'seed-pascucci-2@example.com', '2026-07-09 10:00:00', '2026-07-09 10:00:00'),
    (10, 14, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, 3, '혼잡한 시간대 휴게시간 운영은 보완되면 좋겠습니다.', 'APPROVED', 'seed-pascucci-3@example.com', '2026-07-10 10:00:00', '2026-07-10 10:00:00'),
    (11, 14, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, 4, '스케줄 변경은 조금 더 일찍 공유되면 좋겠습니다.', 'APPROVED', 'seed-pascucci-4@example.com', '2026-07-11 10:00:00', '2026-07-11 10:00:00'),
    (12, 14, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, 3, '정리된 매뉴얼 덕분에 업무 적응이 수월했습니다.', 'APPROVED', 'seed-pascucci-5@example.com', '2026-07-12 10:00:00', '2026-07-12 10:00:00'),
    (13, 14, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, TRUE, 3, '마감 시간 급여와 초과근무 안내가 더 명확하면 좋겠습니다.', 'APPROVED', 'seed-pascucci-6@example.com', '2026-07-13 10:00:00', '2026-07-13 10:00:00');

ALTER TABLE workspaces ALTER COLUMN workspace_id RESTART WITH 15;
ALTER TABLE reviews ALTER COLUMN review_id RESTART WITH 14;
