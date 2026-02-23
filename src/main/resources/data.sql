-- 이미 데이터가 있으면 건너뛰기 위해 INSERT IGNORE 사용
INSERT IGNORE INTO event (id, name, description, status, starts_at, ends_at, created_at, updated_at)
VALUES
  (1, '2026 봄 콘서트', '봄맞이 특별 콘서트', 'OPEN', '2026-03-15 19:00:00', '2026-03-15 21:30:00', NOW(), NOW()),
  (2, '뮤지컬 갈라쇼', '인기 뮤지컬 하이라이트 공연', 'OPEN', '2026-04-01 18:00:00', '2026-04-01 20:30:00', NOW(), NOW()),
  (3, '재즈 페스티벌', '주말 재즈 페스티벌', 'DRAFT', '2026-05-10 17:00:00', '2026-05-10 22:00:00', NOW(), NOW());

INSERT IGNORE INTO seat (id, event_id, section, total_count, reserved_count, price, created_at, updated_at)
VALUES
  (1, 1, 'VIP', 50, 0, 150000, NOW(), NOW()),
  (2, 1, 'R', 100, 0, 100000, NOW(), NOW()),
  (3, 1, 'S', 200, 0, 70000, NOW(), NOW()),
  (4, 2, 'VIP', 30, 0, 200000, NOW(), NOW()),
  (5, 2, 'R', 80, 0, 130000, NOW(), NOW()),
  (6, 2, 'S', 150, 0, 80000, NOW(), NOW()),
  (7, 3, 'A', 100, 0, 90000, NOW(), NOW()),
  (8, 3, 'B', 200, 0, 60000, NOW(), NOW());
