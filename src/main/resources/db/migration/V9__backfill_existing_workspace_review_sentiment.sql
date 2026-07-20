UPDATE reviews
SET sentiment = 'NEGATIVE'
WHERE status = 'APPROVED'
  AND sentiment IS NULL
  AND EXISTS (
      SELECT 1
      FROM workspaces
      WHERE workspaces.workspace_id = reviews.workspace_id
        AND workspaces.name IN ('탑독PC', '더벤티 전남대', '디저트39 전대점')
  );

UPDATE reviews
SET sentiment = 'NEUTRAL'
WHERE status = 'APPROVED'
  AND sentiment IS NULL
  AND EXISTS (
      SELECT 1
      FROM workspaces
      WHERE workspaces.workspace_id = reviews.workspace_id
        AND workspaces.name = '공차 전남대점'
  );

UPDATE reviews
SET sentiment = 'POSITIVE'
WHERE status = 'APPROVED'
  AND sentiment IS NULL
  AND EXISTS (
      SELECT 1
      FROM workspaces
      WHERE workspaces.workspace_id = reviews.workspace_id
        AND workspaces.name IN ('도토리베이커리 용봉점', '군함맥주 전남대점', '파스쿠찌 전남대점', '맥도날드 전남대DT점')
  );
