# Step 4. 쿼리 튜닝 — 실험 기록

Step 2에서 병목으로 확인된 "인덱스 없는 전체 테이블 스캔"을 출발점으로, 주요 조회 쿼리 5개를 `EXPLAIN (ANALYZE, BUFFERS)`로 측정하며 인덱스를 하나씩 추가한 기록입니다. 인덱스만으로 해결되지 않은 부분은 정렬 메모리(`work_mem`)와 **데이터의 물리 저장 순서(correlation)**까지 따라가 확인했고, 마지막으로 Step 2 배치 실험을 같은 조건으로 다시 측정했습니다.

채택한 인덱스 DDL은 [`docs/03_indexes.sql`](../03_indexes.sql)에 있습니다.

## 요약

| 실험 | 결과 | 결론 |
|---|---|---|
| [1. 기준값](#1-기준값) | 배치 Reader 1일 2,408ms, 회원 주문 266ms, 원본 대시보드 1,288ms | 모두 Seq Scan. 하루치(0.25%)를 찾으려고 orders·order_item 2GB를 읽음 |
| [2. `orders(order_time)`](#2-ordersorder_time) | orders 접근 1.9s → 0.2s, 쿼리 전체 2,408 → 2,186ms | 조인 상대(order_item) 풀스캔이 남아 효과 제한 |
| [3. `order_item(order_id)` 일반 vs 커버링](#3-order_itemorder_id-일반-vs-커버링) | 2,186 → **528ms**. 커버링은 일반 대비 15% 빠르지만 크기 2.2배 | 일반 인덱스 채택 |
| [4. FK 인덱스](#4-fk-인덱스-회원-주문판매자-대시보드) | 회원 주문 266 → **28ms**, 원본 대시보드 1,288 → **265ms** | FK에는 인덱스가 자동으로 생기지 않음 |
| [5. 복합 인덱스 컬럼 순서](#5-복합-인덱스-컬럼-순서) | `(member_id, order_time)` 0.08ms vs `(order_time, member_id)` **294ms** (주문 없는 회원) | 평균은 비슷해도 최악의 경우 약 3,900배 차이 |
| [6. `work_mem`](#6-work_mem-디스크-정렬) | 월 집계 15.7s → **10.4s**, 디스크 정렬 97MB 제거 | 인덱스로 해결되지 않는 정렬·해시 비용 |
| [7. 물리 저장 순서](#7-물리-저장-순서-correlation) | CLUSTER만: 1일 528 → 718ms(악화). id 시간순 재구성: **380ms** | id·시각·저장 순서가 함께 맞아야 효과. 실서비스는 시간순 INSERT로 자연히 이 상태 |
| [8. 배치 2라운드](#8-배치-2라운드) | Reader: Cursor 9.2 → **1.9s**, No-Offset 76.2 → **7.5s**<br>Partitioning gridSize 8: 15.3 → **5.9s** | 파티션 기간이 짧아야 인덱스 계획을 탐. 단일 스레드 대비 5배 |

**한 줄 요약**: 배치 Reader 쿼리 **2.4s → 0.38s (6.3배)**, 월 집계 배치 **24.6s → 5.9s (4.2배, Step 2 단일 스레드 대비)**.

---

## 실험 환경

| 항목 | 값 |
|---|---|
| 데이터 | 주문 10,000,000 / 주문상품 19,997,654 / 상품 100,000 / 판매자 1,000 / 회원 1,000 |
| DB | PostgreSQL 16.14 (Docker), `shared_buffers` 128MB, `work_mem` 4MB, `random_page_cost` 4, `max_parallel_workers_per_gather` 2 |
| Docker 리소스 | vCPU 16, 메모리 6.9GB |
| 배치 | Step 2와 동일 (Java 21, Spring Boot 4.0.2, Spring Batch 6.0.2, 프로필 `local,bench`) |

### 측정 방법
- **쿼리**: `EXPLAIN (ANALYZE, BUFFERS)`의 `Execution Time`. 조건별 3회(7·8절은 5회) 연속 실행, **중앙값**
- **1회차 제외 경향**: 1회차는 캐시가 식어 크게 튀는 경우가 많아 중앙값을 씀. 1회차 값도 원본 로그에 남겨 둠
- **A/B 교차 측정**: 차이가 작거나 환경 노이즈가 의심될 때는 두 조건을 번갈아 실행. 인덱스 비교는 트랜잭션 안에서 한쪽 인덱스를 `DROP` → 측정 → `ROLLBACK`하는 방식으로 두 인덱스를 공존시킨 채 측정
- **배치**: Step 2와 같은 방식(조건 교차 3세트, `BATCH_STEP_EXECUTION`의 Step 실행 시간, 중앙값)
- **정확성**: 데이터 재구성과 배치 실행 전후로 집계 결과의 md5를 비교 (모두 일치)

### 대상 쿼리

| 번호 | 쿼리 | 실제 쓰임 |
|---|---|---|
| **Q1-day** | 주문 × 주문상품 × 상품 조인, 9/15 하루, 날짜·판매자·주문 순 정렬 (50,332행) | Step 2 배치 Reader (파티션 하나) |
| **Q1-month** | 같은 쿼리, 9월 전체 (1,513,693행) | Step 2 배치 Reader (단일 스레드) |
| **Q3** | `SELECT * FROM orders WHERE member_id = 500` (10,091행) | 회원 주문 목록 |
| **Q3p** | Q3 + `ORDER BY order_time DESC LIMIT 20` | 회원 최근 주문 페이징 |
| **Q4** | 집계 테이블에서 판매자 500의 9월 30일치 | Step 3 대시보드 (집계 테이블) |
| **Q5** | 같은 결과를 원본 테이블에서 직접 집계 | Step 3 대시보드 (원본 직접 집계) |

<details>
<summary>Q1 쿼리 전문</summary>

```sql
SELECT o.id, i.seller_id, CAST(o.order_time AS date), o.status,
       SUM(oi.quantity), SUM(oi.order_price * oi.quantity)
FROM orders o
JOIN order_item oi ON oi.order_id = o.id
JOIN item i ON i.id = oi.item_id
WHERE o.order_time >= '2026-09-15' AND o.order_time < '2026-09-16'
  AND o.status IN ('PAID', 'CANCEL')
GROUP BY o.id, i.seller_id, o.order_time, o.status
ORDER BY CAST(o.order_time AS date), i.seller_id, o.id;
```
</details>

---

## 1. 기준값

인덱스는 PK뿐인 상태입니다.

| 쿼리 | 1회 | 2회 | 3회 | **중앙값** |
|---|---|---|---|---|
| Q1-day | 4,246 | 2,408 | 1,878 | **2,408ms** |
| Q1-month | 6,585 | 6,127 | 5,838 | **6,127ms** |
| Q3 | 268 | 242 | 266 | **266ms** |
| Q4 | 5.1 | 0.24 | 0.25 | **0.25ms** |
| Q5 | 2,892 | 1,288 | 1,004 | **1,288ms** |

### 실행 계획

**Q1-day**: 하루치를 찾으려고 2GB를 읽습니다.
```
Parallel Seq Scan on orders       Rows Removed by Filter: 4,987,430 (× 2 workers)
Parallel Seq Scan on order_item   rows=9,998,827 (× 2 workers)
Buffers: read=263,878 (약 2.0GB)
Sort Method: external merge  Disk: 3,256kB
```
orders 1,000만 행의 99.75%를 읽고 버리고, order_item 2,000만 행을 모두 해시 조인합니다.

**Q1-month**: 정렬과 해시가 `work_mem` 4MB를 넘어 디스크로 넘어갑니다.
```
Sort Method: external merge  Disk: 97,816kB  (최종 정렬, 워커별 부분 정렬 약 25MB × 3 별도)
```

**Q3**: 회원 1명의 주문 1만 건을 찾으려고 950MB(121,581페이지)를 읽습니다.

**Q4 vs Q5**: 같은 대시보드 결과인데 집계 테이블은 Bitmap Index Scan으로 30행만 읽어 0.25ms, 원본 직접 집계는 order_item 2,000만 행을 스캔해 1,288ms입니다. **약 5,000배 차이**로, Step 3에서 집계 테이블을 쓰는 근거입니다.

### 물리 저장 순서
| 컬럼 | correlation |
|---|---|
| `orders.order_time` | **-0.004** |
| `order_item.order_id` | 1.0 |

correlation은 컬럼 값의 순서와 디스크 저장 순서가 얼마나 일치하는지를 나타냅니다(1이면 완전히 일치). 더미 데이터 시더가 주문 id는 순서대로, 주문 시각은 무작위로 넣었기 때문에 같은 날 주문이 테이블 전체에 흩어져 있습니다. 실서비스에서는 주문이 시간순으로 들어오므로 1에 가깝습니다. 이 차이는 [7절](#7-물리-저장-순서-correlation)에서 다룹니다.

---

## 2. `orders(order_time)`

```sql
CREATE INDEX idx_orders_order_time ON orders (order_time);   -- 207MB, 8.8s
```

| 쿼리 | 전 | 후 |
|---|---|---|
| Q1-day | 2,408ms | **2,186ms** (6,313 / 2,072 / 2,186) |

```
Parallel Bitmap Heap Scan on orders o   (actual time=32..210ms)  Heap Blocks: exact=13,661
  -> Bitmap Index Scan on idx_orders_order_time  rows=27,369
Parallel Seq Scan on order_item oi      Buffers: read=171,786   ← 그대로
```

orders 접근은 Seq Scan(약 1.9s)에서 Bitmap Index Scan(0.2s)으로 바뀌었지만, **order_item 2,000만 행 풀스캔이 그대로 남아** 전체 시간은 거의 줄지 않았습니다. 하루치 2.7만 건이 페이지 13,661개에 흩어져 있다는 것도 보입니다(correlation ≈ 0).

Q1-month에서는 9월 83만 건이 거의 모든 페이지에 흩어져 있어 Bitmap이 lossy로 바뀌고 orders 페이지 12.7만 개를 사실상 전부 읽었습니다. 월 단위에서는 인덱스 효과가 거의 없습니다.

---

## 3. `order_item(order_id)` 일반 vs 커버링

### 일반 인덱스
```sql
CREATE INDEX idx_order_item_order_id ON order_item (order_id);   -- 357MB, 9.0s
```

| 쿼리 | 전 | 후 |
|---|---|---|
| Q1-day | 2,186ms | **528ms** (736 / 528 / 463) |

```
Nested Loop
  -> Parallel Bitmap Heap Scan on orders o           rows=12,570 × 2
  -> Index Scan using idx_order_item_order_id on order_item oi   loops=25,140
```

해시 조인이 Nested Loop로 바뀌어 주문 2.5만 건의 주문상품만 찾아갑니다. **Q1-day 기준값 대비 4.6배**입니다.

### 커버링 인덱스
```sql
CREATE INDEX idx_order_item_order_id_cover ON order_item (order_id)
    INCLUDE (item_id, quantity, order_price);   -- 773MB, 36.6s
```
쿼리에 필요한 컬럼을 인덱스 리프에 함께 담아 테이블(힙) 방문 없이 `Index Only Scan`으로 끝냅니다.

두 인덱스를 함께 두고 트랜잭션 안에서 한쪽만 `DROP` → 측정 → `ROLLBACK`하는 방식으로 번갈아 5회씩 측정했습니다.

| | 1회 (캐시 식음) | 2~5회 | **중앙값** | order_item 1회 조회 | 디스크 읽기 페이지 |
|---|---|---|---|---|---|
| 일반 | 4,872 | 463 · 450 · 475 · 453 | **463ms** | 0.012ms | 42,828 |
| 커버링 | 3,873 | 390 · 376 · 392 · 402 | **392ms** | 0.008ms | 22,786 |
| 차이 | -20% | | **-15%** | -33% | -47% |

```
Index Only Scan using idx_order_item_order_id_cover   Heap Fetches: 0
```

**결론: 일반 인덱스 채택.**
- `order_item.order_id`의 correlation이 1이라 일반 인덱스의 힙 방문도 이미 순차에 가깝습니다. 커버링이 아낄 수 있는 몫이 작습니다.
- 15% 개선의 대가로 인덱스가 416MB 더 크고, 생성 시간이 4배이며, 주문 INSERT마다 더 큰 인덱스를 갱신해야 합니다.
- 커버링은 힙 접근이 랜덤이라 비싼 경우나 호출 빈도가 매우 높은 조회에서 고려합니다. 또 visibility map이 채워져 있어야(VACUUM) `Heap Fetches`가 0으로 유지됩니다.

---

## 4. FK 인덱스 (회원 주문·판매자 대시보드)

Hibernate와 PostgreSQL은 FK 컬럼에 인덱스를 자동으로 만들지 않습니다.

### 회원 주문 (Q3)
```sql
CREATE INDEX idx_orders_member_id ON orders (member_id);   -- 67MB, 5.4s (5절에서 복합 인덱스로 교체)
```

| 쿼리 | 전 | 후 |
|---|---|---|
| Q3 | 266ms | **28ms** (126 / 25.4 / 27.8) |

```
Bitmap Heap Scan on orders   Heap Blocks: exact=9,711
  -> Bitmap Index Scan on idx_orders_member_id   rows=10,091
```

단일 컬럼 인덱스가 67MB로 작은 이유는 B-tree 중복 제거(PostgreSQL 13+) 때문입니다. 회원이 1,000명뿐이라 같은 키가 1만 번씩 반복되어 압축됩니다.

이 API는 페이징 없이 1만 건을 한 번에 반환한다는 설계 문제도 함께 드러났습니다. → 5절의 Q3p.

### 판매자 대시보드 원본 집계 (Q5)
```sql
CREATE INDEX idx_item_seller_id ON item (seller_id);              -- 0.7MB, 0.2s
CREATE INDEX idx_order_item_item_id ON order_item (item_id);      -- 133MB, 16.7s
```

| 쿼리 | 전 | 후 |
|---|---|---|
| Q5 | 1,288ms | **265ms** (3,001 / 265 / 255) |

```
Nested Loop
  -> Seq Scan on item i                      rows=100 (판매자 500의 상품)
  -> Bitmap Heap Scan on order_item oi       loops=100, rows=202
  -> Index Scan using orders_pkey on orders  loops=20,154
```

order_item 전체 스캔이 "판매자 상품 100개 → 주문상품 2만 건" 조회로 바뀌었습니다. 그래도 집계 테이블(Q4, 0.25ms)보다 1,000배 느립니다.

---

## 5. 복합 인덱스 컬럼 순서

쿼리는 회원의 최근 주문 20건입니다.
```sql
SELECT * FROM orders WHERE member_id = ? ORDER BY order_time DESC LIMIT 20;
```
인덱스가 `(member_id, order_time)` 순서로 정렬되어 있으면, 해당 회원 구간의 끝에서 거꾸로 20건만 읽고 멈춥니다(`Index Scan Backward`). 정렬이 필요 없습니다.

| 인덱스 | 회원 500 (주문 1만 건) | 주문 없는 회원 |
|---|---|---|
| `member_id` 단일 | 54ms | – |
| `(order_time, member_id)` 순서 틀림 | 0.59ms | **294ms** |
| `(member_id, order_time)` 올바름 | **0.33ms** | **0.08ms** |

```
-- member_id 단일: 플래너가 order_time 인덱스를 거꾸로 훑으며 다른 회원 행을 버림
Index Scan Backward using idx_orders_order_time   Rows Removed by Filter: 15,797

-- (order_time, member_id), 주문 없는 회원: 인덱스 전체를 거꾸로 훑음
Index Scan Backward using idx_orders_time_member   rows=0   Buffers: read=38,306

-- (member_id, order_time): 회원 구간만
Index Scan Backward using idx_orders_member_time   Buffers: hit=26
```

**결론**: 동등 조건 컬럼(`member_id`)을 앞에, 정렬·범위 컬럼(`order_time`)을 뒤에 둡니다. 순서가 틀린 인덱스는 주문이 고르게 많은 회원에게서는 빨라 보입니다. 하지만 최근 주문이 없는 회원처럼 조건에 맞는 행이 드문 경우 인덱스 전체를 훑게 되어 **최악의 경우 약 3,900배 차이**가 납니다. 평균만 보면 놓치는 문제입니다.

`(member_id, order_time)`이 `member_id` 단일 인덱스의 역할(Q3)도 대신하므로 단일 인덱스는 삭제했습니다. 크기는 301MB로, 단일 인덱스(67MB)와 달리 키가 고유해서 중복 제거가 되지 않습니다.

---

## 6. `work_mem` (디스크 정렬)

Q1-month는 인덱스를 추가해도 빨라지지 않았습니다. 병목이 151만 행의 정렬과 해시 조인이 디스크로 넘어가는 데 있기 때문입니다.

배치 Reader는 JDBC 커서(fetchSize)로 읽는데, 이 경로에서는 PostgreSQL이 **병렬 쿼리를 쓰지 않습니다.** 그래서 실제 배치와 같은 조건이 되도록 `max_parallel_workers_per_gather = 0`으로 두고 측정했습니다. 병렬 상태에서 `work_mem`을 올리면 병렬 해시 조인이 Docker 공유 메모리(`/dev/shm` 64MB)를 넘어 오류가 났다는 이유도 있습니다.

| `work_mem` | 1회 | 2회 | 3회 | **중앙값** | 실행 계획 |
|---|---|---|---|---|---|
| 4MB (기본) | 15,827 | 14,219 | 15,718 | **15,718ms** | 정렬 external merge 97MB, 해시 집계 161 배치 / 디스크 96MB |
| 64MB | 10,569 | 10,195 | 10,728 | **10,569ms** | 해시는 메모리, 정렬은 여전히 디스크 97MB |
| 256MB | 10,397 | 10,440 | 10,367 | **10,397ms** | 정렬 quicksort 메모리 167MB, 디스크 0 |

**34% 단축**. 서버 전체 `work_mem`을 올리면 동시 접속 수 × 정렬 노드 수만큼 메모리를 쓸 수 있어 위험합니다. 그래서 큰 정렬을 하는 배치 커넥션에서만 세션 단위로(`SET work_mem`) 올리는 것이 적절합니다. 이번 실험에서는 측정만 했고 배치 코드에는 적용하지 않았습니다.

---

## 7. 물리 저장 순서 (correlation)

1절에서 본 것처럼 더미 데이터는 `order_time`의 correlation이 약 0이라, 인덱스 효과가 실서비스보다 나쁘게 측정될 수 있습니다. 두 단계로 확인했습니다.

### 7-1. CLUSTER로 시각순 재배치
```sql
CLUSTER orders USING idx_orders_order_time;   -- 3분 14초, 실행 중 ACCESS EXCLUSIVE 잠금
VACUUM (PARALLEL 0, ANALYZE) orders;          -- 39.5초, visibility map 재생성
```
`CLUSTER`는 테이블을 인덱스 순서대로 다시 써서 물리적으로 정렬하는 일회성 명령입니다. `order_time` correlation이 0.008에서 1이 되었습니다.

| 쿼리 | CLUSTER 전 | CLUSTER 후 (5회 중앙값) | orders 접근만 |
|---|---|---|---|
| Q1-day | 528ms | **718ms** ↑ | Bitmap Heap Scan 115ms → Index Scan 46ms |
| Q3 | 28ms | 33ms | 변화 없음 (member_id는 여전히 흩어짐) |
| Q5 | 265ms | 470ms ↑ | – |

orders 접근은 2.5배 빨라졌지만 쿼리 전체는 **오히려 느려졌습니다.**

```
order_item 1회 조회: 0.013ms → 0.022ms
corr(orders.id, order_time) = -0.004
```

- CLUSTER 전에는 하루치 주문을 Bitmap으로 **물리 순서 = id 순서**대로 읽었습니다. 그래서 order_item 조회도 order_id 오름차순으로 진행되어 order_item을 순차로 읽었습니다.
- CLUSTER 후에는 orders를 시각 순서로 읽는데, 시더가 시각을 무작위로 넣었기 때문에 **id가 무작위 순서로 나옵니다.** 그래서 order_item을 랜덤으로 읽게 되었습니다.

한 테이블은 한 가지 순서로만 정렬할 수 있습니다. orders의 순서를 바로잡자 orders → order_item 지역성이 깨진 것입니다.

### 7-2. id를 시각순으로 다시 부여 (실서비스 재현)
실서비스는 id(시퀀스)와 주문 시각이 함께 증가하므로 orders, order_item, 시각이 모두 같은 순서입니다. 이를 재현하려고 orders와 order_item을 시각순 id로 다시 만들었습니다([부록 B](#b-id-시각순-재구성)).

| 항목 | 결과 |
|---|---|
| 소요 | 약 6분 (orders 1분 23초, order_item 1분 55초, PK·인덱스·FK·VACUUM 나머지) |
| 내용 검증 | 날짜·판매자·상태별 주문 수, 수량, 금액 md5와 회원별 주문 수·첫/마지막 주문 시각 md5가 재구성 전후 일치. 행 수 동일 |
| correlation | `orders.id` 1, `orders.order_time` 1, `order_item.id` 1, `order_item.order_id` 1 |

| 쿼리 | 기준값 | 인덱스 추가 | CLUSTER만 | **시각순 재구성** |
|---|---|---|---|---|
| Q1-day | 2,408 | 528 | 718 | **380ms** (380 / 531 / 475 / 326 / 334) |
| Q3 | 266 | 28 | 33 | **18.5ms** |
| Q3p (회원 500) | – | 0.33 | 0.33 | **0.12ms** |
| Q5 | 1,288 | 265 | 470 | 290ms |

```
Q1-day  Buffers: shared hit=104,254 (디스크 읽기 0)
        Index Scan using idx_order_item_order_id   (actual time=0.002ms × 25,140 loops)
```

하루치 주문과 그 주문상품이 각각 연속된 페이지에 모여 있어서 모든 접근이 캐시에서 끝났습니다. **Q1-day 기준값 대비 6.3배.**

### Q1-month에 대한 추가 시도
재구성 후에도 Q1-month(병렬 끔, `work_mem` 256MB)는 11,413ms였습니다. 9월 주문상품이 연속된 1/12 구간에 모여 있는데도 플래너는 여전히 order_item 전체를 Seq Scan + Hash Join으로 읽습니다.

| 시도 | 중앙값 | 결과 |
|---|---|---|
| `enable_hashjoin = off` (Merge Join 유도) | 14,109ms | order_item 인덱스를 처음부터 끝까지 훑어 더 느림 |
| `oi.order_id BETWEEN (9월 최소 id) AND (최대 id)` 조건 추가 | 12,734ms | min/max 서브쿼리 3.2s, 이후 item 조회 151만 번 |

실행 계획상 order_item 스캔은 약 1.9초이고, 나머지는 **151만 행을 조인·집계·정렬하는 CPU 비용**입니다. 이건 쿼리 하나로 줄이기보다 작업을 나눠서 풀 문제이고, 배치 Partitioning이 그 해법입니다(8절).

### 정리
- 인덱스의 실제 I/O 패턴은 **인덱스 순서와 물리 저장 순서의 상관관계**가 결정합니다. 같은 인덱스라도 correlation에 따라 0.38s와 0.72s로 갈렸습니다.
- CLUSTER는 한 가지 순서만 맞출 수 있고, 일회성이며(이후 INSERT부터 순서가 유지되지 않음), 실행 중 테이블 전체를 잠급니다. 운영에서는 거의 쓰지 않습니다.
- 실서비스는 시간순 INSERT만으로 자연히 이 상태가 됩니다. **실험 데이터를 만들 때도 실제 데이터의 물리적 특성을 반영해야** 측정이 의미가 있습니다. 시더도 id 순서대로 시각이 증가하도록 수정합니다.

---

## 8. 배치 2라운드

인덱스와 시각순 데이터 조건에서 Step 2 실험을 같은 파라미터로 다시 실행했습니다(job execution 37~57, `run=201~221`). 실행 후 집계 테이블 md5가 실행 전과 일치했습니다.

### Reader 3종 (9/15 하루, chunk 1,000)

| Reader | 1세트 | 2세트 | 3세트 | **중앙값** | Step 2 | 개선 |
|---|---|---|---|---|---|---|
| Cursor | 3.3 | 1.9 | 1.8 | **1.9s** | 9.2s | 4.8배 |
| JPA OFFSET | 10.1 | 10.2 | 10.8 | **10.2s** | 82.0s | 8.0배 |
| No-Offset | 7.6 | 7.5 | 7.5 | **7.5s** | 76.2s | **10.2배** |

- 페이징 Reader는 페이지 51개마다 쿼리를 다시 실행하므로, 쿼리 하나가 1.6s Seq Scan에서 인덱스 조회로 바뀐 효과가 51배로 쌓여 개선 폭이 가장 큽니다.
- Step 2에서는 No-Offset과 OFFSET의 차이(7%)가 페이지마다 하는 풀스캔에 묻혀 유의미하지 않았습니다. 이번에는 **세 세트 모두 No-Offset이 26% 빨랐습니다.** 풀스캔 비용이 사라지자 OFFSET이 앞의 행을 읽고 버리는 비용이 드러났습니다.
- Cursor는 쿼리를 한 번만 실행하므로 여전히 가장 빠릅니다. 기본값 유지.

### Partitioning (9월, Cursor, chunk 5,000)

| gridSize | 1세트 | 2세트 | 3세트 | **중앙값** | 단일 대비 | Step 2 |
|---|---|---|---|---|---|---|
| 1 | 30.1 | 29.5 | 25.8 | **29.5s** | 1.0배 | 24.6s |
| 2 | 21.6 | 23.2 | 24.9 | **23.2s** | 1.3배 | 22.5s |
| 4 | 22.1 | 33.0 | 24.2 | **24.2s** | 1.2배 | 28.2s |
| 8 | 5.6 | 6.2 | 5.9 | **5.9s** | **5.0배** | 15.3s |

gridSize 1·2·4는 Step 2와 거의 같고, gridSize 8에서만 급격히 빨라졌습니다.

#### 원인: 파티션 기간이 실행 계획을 바꿈
한 번 더 실행하면서 `pg_stat_user_tables`의 스캔 카운터 변화를 측정했습니다(job execution 58, 59).

| 실행 | order_item Seq Scan | Seq Scan으로 읽은 행 | order_item Index Scan | 소요 |
|---|---|---|---|---|
| gridSize 4 (파티션당 7~8일) | **+4회** | +79,990,616 | +16 | 34.6s |
| gridSize 8 (파티션당 3~4일) | **0회** | 0 | **+757,032** | 7.1s |

- **파티션이 7~8일 이상**이면 플래너가 order_item 풀스캔 + Hash Join을 고릅니다. 파티션마다 2,000만 행 스캔이 반복되어 Step 2와 같은 고정 비용이 남습니다. gridSize 4의 파티션 4개는 담당 행 수(35만~40만)와 상관없이 모두 21~33초로 같았습니다.
- **파티션이 3~4일 이하**면 주문마다 `idx_order_item_order_id`로 찾아가는 Nested Loop를 고릅니다. 각 파티션이 자기 범위만 읽습니다.
- gridSize 8의 파티션 시간은 처리량에 비례합니다(4일 담당 5.5~5.9s, 3일 담당 4.8~5.0s). Step 2에서 파티션 시간의 64~88%였던 고정 비용이 사라졌습니다.

> psql에서 같은 4일 범위를 리터럴로 넣고 `EXPLAIN`하면 Hash Join이 나와 배치 실행과 다릅니다. JDBC가 날짜 파라미터를 바인딩하는 방식 때문에 행 수 추정이 달라지는 것으로 보이며, 아직 정확한 원인은 확인하지 못했습니다. 실제 배치의 동작은 위 카운터로 확인했습니다.

#### 결론
파티션을 "스레드 수"가 아니라 **"인덱스 계획을 타는 기간 단위"**로 나눠야 합니다. 현재 구현은 `concurrencyLimit = gridSize`라 파티션 수와 스레드 수가 묶여 있습니다. 하루 단위로 30개 파티션을 만들고 동시 실행 수만 4~8로 제한하면, 스레드 수와 관계없이 항상 인덱스 계획을 탈 수 있습니다(후속 과제).

---

## 9. 인덱스 비용

| 인덱스 | 크기 | 생성 시간 | 채택 |
|---|---|---|---|
| `orders(order_time)` | 207MB | 8.8s | ✅ |
| `orders(member_id)` | 67MB | 5.4s | ❌ 복합 인덱스로 대체 |
| `orders(member_id, order_time)` | 301MB | 9.0s | ✅ |
| `orders(order_time, member_id)` | – | 10.6s | ❌ 순서 비교용 |
| `order_item(order_id)` | 357MB | 9.0s | ✅ |
| `order_item(order_id) INCLUDE (item_id, quantity, order_price)` | 773MB | 36.6s | ❌ 크기 대비 효과 15% |
| `order_item(item_id)` | 133MB | 16.7s | ✅ |
| `item(seller_id)` | 0.7MB | 0.2s | ✅ |

채택한 인덱스는 합계 약 1GB로, 테이블(orders 1.0GB + order_item 1.15GB)의 절반 정도입니다. 주문 INSERT마다 orders 인덱스 3개(PK 포함)와 order_item 인덱스 3개를 갱신해야 합니다. 쓰기 처리량 측정은 Step 3 부하 테스트에서 함께 확인합니다.

운영 DB에 적용할 때는 `CREATE INDEX CONCURRENTLY`로 쓰기 잠금 없이 만들고, 이후 `ANALYZE`로 통계를 갱신합니다([`03_indexes.sql`](../03_indexes.sql)).

---

## 측정 시 주의했던 점

- **캐시 노이즈**: Docker 메모리(6.9GB)에 비해 테이블과 인덱스 합이 커서, 인덱스를 추가할수록 OS 페이지 캐시 경쟁이 생겨 Q1-month가 6s에서 8~13s 사이로 흔들렸습니다. 인덱스를 켠 경우와 끈 경우(`enable_*scan = off`)를 번갈아 측정해(8.3s vs 9.5s), 느려진 원인이 인덱스가 아니라 환경이라는 것을 확인했습니다. CLUSTER와 재구성 직후에도 새 파일이라 1회차가 6~34s로 튀었습니다.
- **EXPLAIN과 배치의 차이**: EXPLAIN의 병렬 계획은 커서로 읽는 배치보다 낙관적이고, 파라미터 바인딩에 따라 계획이 달라질 수 있습니다. 배치 실험은 EXPLAIN이 아니라 실제 실행과 테이블 통계 카운터로 판단했습니다.
- **visibility map**: CLUSTER나 재구성 후에는 `relallvisible = 0`이라 Index Only Scan이 힙을 방문합니다. 측정 전에 VACUUM으로 조건을 맞췄습니다.
- **병렬 VACUUM 실패**: Docker 기본 `/dev/shm`(64MB)이 부족해 `VACUUM ANALYZE`가 실패해서 `VACUUM (PARALLEL 0, ANALYZE)`로 실행했습니다.

---

## 부록

### A. 측정 명령
```bash
# 쿼리별 EXPLAIN 3회
for N in 1 2 3; do
  { echo "EXPLAIN (ANALYZE, BUFFERS)"; cat q1_day.sql; echo ";"; } \
    | docker exec -i shop-loadtest-postgres psql -U shop -d shop -X -q > q1_day_run$N.txt
done
grep -h 'Execution Time' q1_day_run*.txt
```

인덱스 A/B 교차 측정:
```sql
BEGIN;
DROP INDEX idx_order_item_order_id_cover;   -- 일반 인덱스만 남김
EXPLAIN (ANALYZE, BUFFERS) <Q1-day>;
ROLLBACK;                                   -- DROP 취소
```

배치 스캔 카운터:
```sql
SELECT relname, seq_scan, seq_tup_read, idx_scan
FROM pg_stat_user_tables WHERE relname IN ('orders', 'order_item');
```

### B. id 시각순 재구성
```sql
-- 1) old id → new id (시각순)
CREATE UNLOGGED TABLE order_id_map AS
SELECT id AS old_id, row_number() OVER (ORDER BY order_time, id) AS new_id FROM orders;
CREATE INDEX ON order_id_map (old_id) INCLUDE (new_id);

-- 2) new id 순서로 새 테이블 적재
CREATE TABLE orders_new (LIKE orders INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING IDENTITY);
INSERT INTO orders_new (id, created_at, member_id, order_time, updated_at, city, status, street, zipcode)
SELECT m.new_id, o.created_at, o.member_id, o.order_time, o.updated_at, o.city, o.status, o.street, o.zipcode
FROM orders o JOIN order_id_map m ON m.old_id = o.id
ORDER BY m.new_id;

CREATE TABLE order_item_new (LIKE order_item INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING IDENTITY);
INSERT INTO order_item_new (id, order_price, quantity, item_id, order_id)
SELECT row_number() OVER (ORDER BY m.new_id, oi.id), oi.order_price, oi.quantity, oi.item_id, m.new_id
FROM order_item oi JOIN order_id_map m ON m.old_id = oi.order_id
ORDER BY m.new_id, oi.id;

-- 3) 기존 테이블을 *_old로 이름 변경 후 교체, PK·인덱스·FK 재생성, setval로 시퀀스 맞춤
-- 4) VACUUM (PARALLEL 0, ANALYZE), 검증 통과 후 *_old 삭제
```

### C. 내용 검증 쿼리
```sql
-- 날짜·판매자·상태별 집계의 md5 (id와 무관한 내용 비교)
SELECT md5(string_agg(t::text, '|' ORDER BY t.d, t.seller_id, t.status)) FROM (
  SELECT CAST(o.order_time AS date) d, i.seller_id, o.status,
         count(DISTINCT o.id), sum(oi.quantity), sum(oi.order_price * oi.quantity)
  FROM orders o JOIN order_item oi ON oi.order_id = o.id JOIN item i ON i.id = oi.item_id
  GROUP BY 1, 2, 3) t;

-- 배치 결과 md5
SELECT count(*), md5(string_agg(concat_ws(',', seller_id, sales_date, order_count, item_quantity,
       sales_amount, cancel_count, cancel_amount), '|' ORDER BY seller_id, sales_date))
FROM daily_sales_summary;
```

### D. 배치 Step 시간 조회
```sql
SELECT job_execution_id, round(extract(epoch FROM end_time - start_time)::numeric, 1) AS sec,
       read_count, commit_count
FROM batch_step_execution
WHERE step_name = 'aggregateDailySalesManagerStep' AND job_execution_id BETWEEN 37 AND 59
ORDER BY 1;
```
