-- 부하테스트(k6) 전용 시드 데이터. application-local.yml (spring.sql.init.platform=postgresql) 에서만 로드됨.
-- 비밀번호는 모두 'test1234!' 의 BCrypt 해시.

-- 1) 테스트 유저 20명 (id 1~20)
INSERT INTO member (name, phone, email, password, role, address, created_at, updated_at)
SELECT '부하테스트유저' || gs,
       '010-0000-' || lpad(gs::text, 4, '0'),
       'loadtest' || gs || '@test.com',
       '$2a$10$yeemx1lzL0DQtEGd9bUD7uANo5G3KFIKFQX0DyPZAX/YFmFcw.Fde',
       'USER',
       '서울시 테스트구 테스트로 1',
       now(),
       now()
FROM generate_series(1, 20) AS gs;

-- 2) 재고가 넉넉한 상품 1개 (id 1) - 모든 주문이 이 상품을 참조하게 해서
--    confirm() 의 재고 차감 비관적 락(findByIdWithLock) 경합을 관찰할 수 있게 함
INSERT INTO item (name, price, quantity, description, image_url, status, created_at, updated_at)
VALUES ('부하테스트 상품', 10000, 1000000, '동시성 부하테스트용 상품', NULL, 'SELLING', now(), now());

-- 3) PENDING 주문 1600건 (id 1~1600) - 100+500+1000 동시 요청 시나리오가 각각
--    독립된 주문 구간을 소비하므로 필요한 만큼(100+500+1000=1600) 시딩
INSERT INTO orders (member_id, city, street, zipcode, order_time, status, created_at, updated_at)
SELECT ((gs - 1) % 20) + 1,
       '서울',
       '테스트로 1',
       '00000',
       now(),
       'PENDING',
       now(),
       now()
FROM generate_series(1, 1600) AS gs;

-- 4) 각 주문에 상품 1개씩(수량 1) 연결 -> 주문 총액 10000원으로 고정
INSERT INTO order_item (order_id, item_id, order_price, quantity)
SELECT id, 1, 10000, 1
FROM orders;
