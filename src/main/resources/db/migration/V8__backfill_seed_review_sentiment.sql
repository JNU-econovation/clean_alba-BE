UPDATE reviews
SET sentiment = CASE author_email
    WHEN 'seed:real-data:topdog:1' THEN 'POSITIVE'
    WHEN 'seed:real-data:dessert39:1' THEN 'POSITIVE'
    WHEN 'seed:real-data:dessert39:2' THEN 'NEUTRAL'
    WHEN 'seed:real-data:theventi:1' THEN 'POSITIVE'
    WHEN 'seed:real-data:theventi:2' THEN 'NEUTRAL'
    WHEN 'seed:real-data:theventi:3' THEN 'NEGATIVE'
    WHEN 'seed:real-data:theventi:4' THEN 'POSITIVE'
    WHEN 'seed:real-data:pascucci:1' THEN 'POSITIVE'
    WHEN 'seed:real-data:pascucci:2' THEN 'POSITIVE'
    WHEN 'seed:real-data:pascucci:3' THEN 'NEGATIVE'
    WHEN 'seed:real-data:pascucci:4' THEN 'NEGATIVE'
    WHEN 'seed:real-data:pascucci:5' THEN 'POSITIVE'
    WHEN 'seed:real-data:pascucci:6' THEN 'NEGATIVE'
END
WHERE status = 'APPROVED'
  AND sentiment IS NULL
  AND author_email IN (
      'seed:real-data:topdog:1',
      'seed:real-data:dessert39:1',
      'seed:real-data:dessert39:2',
      'seed:real-data:theventi:1',
      'seed:real-data:theventi:2',
      'seed:real-data:theventi:3',
      'seed:real-data:theventi:4',
      'seed:real-data:pascucci:1',
      'seed:real-data:pascucci:2',
      'seed:real-data:pascucci:3',
      'seed:real-data:pascucci:4',
      'seed:real-data:pascucci:5',
      'seed:real-data:pascucci:6'
  );
