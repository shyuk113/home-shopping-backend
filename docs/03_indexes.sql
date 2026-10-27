-- Step 4 쿼리 튜닝에서 채택한 인덱스
-- 근거와 측정값: docs/experiments/step4-query-tuning.md
--
-- 운영 DB에 적용할 때는 CONCURRENTLY로 만들어 테이블 쓰기를 막지 않는다.
--   - 트랜잭션 블록 안에서는 실행할 수 없으므로 한 문장씩 실행한다.
--   - 실패하면 INVALID 인덱스가 남으므로 DROP INDEX CONCURRENTLY 후 다시 만든다.
--   - 일반 CREATE INDEX보다 테이블을 두 번 읽어 느리다.

-- 배치 Reader: 날짜 범위로 주문 조회 (Q1)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_order_time
    ON orders (order_time);

-- 회원 주문 목록 / 최근 주문 페이징 (Q3, Q3p)
-- 동등 조건(member_id) 컬럼을 앞에, 정렬·범위 컬럼(order_time)을 뒤에 둔다.
-- 순서를 바꾼 (order_time, member_id)는 주문이 없는 회원 조회 시 인덱스 전체를 훑는다 (0.07ms → 295ms).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_member_time
    ON orders (member_id, order_time);

-- 주문 → 주문상품 조인 (Q1). FK에는 Hibernate·PostgreSQL 모두 인덱스를 자동으로 만들지 않는다.
-- 커버링 INCLUDE (item_id, quantity, order_price)는 15% 빨랐지만 크기가 2.2배(357MB → 773MB)라 채택하지 않음.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_item_order_id
    ON order_item (order_id);

-- 판매자 대시보드 원본 집계: 판매자 상품 → 주문상품 (Q5)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_item_item_id
    ON order_item (item_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_item_seller_id
    ON item (seller_id);

-- 플래너 통계 갱신 (correlation, 행 수 추정)
ANALYZE orders;
ANALYZE order_item;
ANALYZE item;
