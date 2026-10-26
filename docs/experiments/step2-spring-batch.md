# Step 2. Spring Batch 일별 매출 집계 — 실험 기록

주문 1,000만 건 위에서 `orders` → `daily_sales_summary` 일별 매출 집계 Job을 구현하고, Writer·Reader·Chunk size·Partitioning을 바꿔가며 처리 시간을 측정한 기록입니다. 마지막으로 실패 후 재시작을 시연했습니다.

모든 수치는 Spring Batch 메타데이터(`BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`)에 남은 실제 실행 기록에서 가져왔습니다. 재현에 필요한 실행 명령과 조회 쿼리는 [부록](#부록)에 있습니다.

## 요약

| 실험 | 결과 | 결론 |
|---|---|---|
| [1. Writer 병목 개선](#1-writer-병목-개선) | 238.9s → **89.8s**, summary UPDATE 1,458,538 → **1,489**회 | Reader 정렬 키를 집계 키 순으로 바꿔 chunk 내 사전 합산 효율을 높임 |
| [2. Reader 3종 비교](#2-reader-3종-비교) | Cursor **9.2s** / JPA OFFSET 82.0s / No-Offset 76.2s | 인덱스 없는 집계 쿼리에서 페이징은 페이지마다 풀스캔을 반복해 약 9배 느림 → Cursor 채택 |
| [3. Chunk size 비교](#3-chunk-size-비교) | 100: 238.3s / 1,000: 53.0s / 5,000: **30.4s** | 커밋 1회당 약 15ms 고정 비용 → 기본값 5,000 채택 (7.8배 단축) |
| [4. Partitioning](#4-partitioning) | gridSize 1: 24.6s / 2: 22.5s / 4: 28.2s / 8: **15.3s** | 최대 1.6배. 파티션마다 풀스캔이 중복되어 확장성 제한 |
| [5. 실패 후 재시작](#5-실패-후-재시작) | 실패한 파티션만, 커밋된 20만 행 이후부터 재처리 | 누락·중복 0, 최종 집계 원본과 일치 |

**공통 결론**: Reader·Partitioning 실험 모두 병목이 **`orders.order_time` 인덱스 부재로 인한 전체 테이블 스캔**으로 수렴했습니다. 다음 단계(Step 4 쿼리 튜닝)에서 인덱스를 설계한 뒤 Reader와 Partitioning을 다시 측정합니다.

---

## 실험 환경

| 항목 | 값 |
|---|---|
| 데이터 | 주문 10,000,000 / 주문상품 19,997,654 / 상품 100,000 / 판매자 1,000 (약 2.8GB) |
| 집계 대상 | 9월 1개월: "주문 × 판매자" 1,513,693행 → 집계 30,000행 (판매자 1,000 × 30일)<br>9/15 하루: 50,332행 → 집계 1,000행 |
| 인덱스 | `orders`, `order_item` 모두 **PK 인덱스만** 존재 (`order_time`, `order_id` 인덱스 없음) |
| DB | PostgreSQL 16.14 (Docker), `max_parallel_workers` 8, `max_parallel_workers_per_gather` 2 |
| Docker 리소스 | vCPU 16, 메모리 6.9GB |
| 애플리케이션 | Java 21, Spring Boot 4.0.2, Spring Batch 6.0.2, HikariCP 풀 50 |
| 실행 프로필 | `local,bench` (`ddl-auto: none`, 적재 데이터 보존) |

### 측정 방법
- **측정값**: 집계 Step의 실행 시간(`BATCH_STEP_EXECUTION.end_time - start_time`). Partitioning은 Manager Step 기준
- **반복**: 조건별 3회, 조건을 번갈아 실행(A→B→C→A→B→C…)해 시간대에 따른 환경 차이를 고르게 분산
- **대표값**: 중앙값. 직전 실행과 시간 간격이 크면 OS·DB 캐시가 식어 첫 실행이 느린 경향이 있어 평균보다 중앙값이 안정적
- **정확성 검증**: 실험마다 Job 결과와 원본 SQL 직접 집계를 양방향 `EXCEPT`로 비교 ([부록 B](#b-정확성-검증-쿼리)). **모든 실험에서 불일치 0건**

---

## Job 구조

```
dailySalesSummaryJob (from, to, reader, chunkSize, gridSize, failOnDate)
├─ deleteDailySalesSummaryStep          대상 기간의 기존 집계 삭제 (재실행 멱등성)
└─ aggregateDailySalesManagerStep       기간을 날짜 단위로 gridSize개 파티션으로 분할
     └─ aggregateDailySalesStep:partitionNN   (파티션별 스레드)
          Reader    : "주문 × 판매자" 행 (주문 내 같은 판매자 상품은 합산)
          Processor : PAID → 매출, CANCEL → 취소
          Writer    : chunk 내 (판매자, 날짜)별 사전 합산 → ON CONFLICT 누적 upsert
```

Reader 쿼리 (Cursor 기준):

```sql
SELECT o.id, i.seller_id, CAST(o.order_time AS date) AS sales_date, o.status,
       SUM(oi.quantity), SUM(oi.order_price * oi.quantity)
FROM orders o
JOIN order_item oi ON oi.order_id = o.id
JOIN item i ON i.id = oi.item_id
WHERE o.order_time >= ? AND o.order_time < ?
  AND o.status IN ('PAID', 'CANCEL')
GROUP BY o.id, i.seller_id, o.order_time, o.status
ORDER BY CAST(o.order_time AS date), i.seller_id, o.id
```

---

## 1. Writer 병목 개선

**조건**: 9월 1개월, `JdbcCursorItemReader`, chunk 1,000

### 문제
첫 구현의 Job 시간은 238.9초였는데, Reader 쿼리만 따로 실행하면 14.1초였습니다. **처리 시간의 약 94%가 Writer**에서 쓰이고 있었습니다.

Reader가 `ORDER BY o.id`(주문 ID 순)로 읽어서, chunk 1,000행 안에 같은 (판매자, 날짜)가 거의 없었습니다. 판매자가 1,000명이라 chunk 안의 행이 거의 다 다른 판매자였기 때문입니다. 그 결과 Writer의 사전 합산이 효과가 없어, 3만 행짜리 집계 테이블에 행마다 UPDATE가 일어났습니다.

### 개선
Reader 정렬을 집계 키 순서로 바꿨습니다.

```sql
ORDER BY o.id, i.seller_id                               -- 변경 전
ORDER BY CAST(o.order_time AS date), i.seller_id, o.id   -- 변경 후
```

같은 (날짜, 판매자) 행이 연달아 나와 chunk 안에서 대부분 합쳐집니다. 마지막 정렬 키가 `o.id`라 정렬 순서가 항상 같으므로, 재시작 시 커서가 건너뛸 위치도 그대로 보장됩니다.

### 결과

| 지표 | 변경 전 (exec 3) | 변경 후 (exec 4) |
|---|---|---|
| Job 시간 | 238.9s | **89.8s (2.7배 단축)** |
| 집계 Step 시간 | 238.5s | 87.0s |
| summary INSERT | 30,000 | 30,000 |
| summary **UPDATE** | **1,458,538** | **1,489 (약 980배 감소)** |
| summary 테이블 크기 | 177MB (MVCC 부풀음) | 7.7MB |

- UPDATE 횟수와 테이블 크기는 `pg_stat_user_tables`로 측정했습니다. 변경 후 측정 전에는 `VACUUM FULL`과 통계 카운터 초기화를 했습니다.
- ⚠️ 각 1회 측정입니다. 변경 폭(2.7배)이 다른 실험에서 관찰된 측정 편차보다 훨씬 커서 방향은 확실합니다.

---

## 2. Reader 3종 비교

**조건**: 9/15 하루(50,332행), chunk 1,000(51페이지), 각 3회

| Reader | 방식 | DB 쿼리 횟수 |
|---|---|---|
| `JdbcCursorItemReader` | 쿼리 1번 + 커서로 1,000행씩 수신 (`connectionAutoCommit(false)` + `fetchSize`) | 1 |
| `JpaPagingItemReader` | 페이지마다 JPQL + `OFFSET` | 51 |
| `NoOffsetSalesRowReader` (직접 구현) | 페이지마다 `WHERE (날짜, 판매자, 주문ID) > (마지막 키)` | 51 |

하루 구간으로 측정한 이유가 있습니다. 9월 전체(1,514페이지)를 페이징 Reader로 돌리면 페이지당 비용 때문에 약 6시간이 걸립니다.

### 원본 데이터 (집계 Step 시간, 초)

| 세트 | Cursor | JPA OFFSET | No-Offset |
|---|---|---|---|
| 1 (exec 5~7) | 9.2 | 82.7 | 82.1 |
| 2 (exec 8~10) | 12.7 | 82.0 | 74.7 |
| 3 (exec 11~13) | 4.6 | 77.9 | 76.2 |

9회 모두 `read=50,332`, `commit=51`로 동일합니다.

### 결과

| Reader | 중앙값 | 범위 | Cursor 대비 |
|---|---|---|---|
| **Cursor** | **9.2s** | 4.6 ~ 12.7s | 1.0× |
| JPA OFFSET | 82.0s | 77.9 ~ 82.7s | 8.9배 느림 |
| No-Offset | 76.2s | 74.7 ~ 82.1s | 8.3배 느림 |

### 원인 분석 — 실행 계획
No-Offset 페이지 쿼리 하나를 `EXPLAIN ANALYZE`로 실행했습니다.

```
Parallel Seq Scan on orders o         Rows Removed by Filter: 4,987,430 (× 2 workers)
Parallel Seq Scan on order_item oi    actual rows=9,998,827 (× 2 workers)
Execution Time: 1593 ms
```

- 하루치를 찾기 위해 **orders 1,000만, order_item 2,000만 행을 페이지마다 전부 스캔**합니다.
- 페이지당 약 1.6초 × 51페이지 ≈ 80초로, 측정값과 일치합니다.
- **OFFSET vs No-Offset 차이(중앙값 7%)는 두 범위가 겹쳐 유의미하지 않습니다.** OFFSET이 버리는 행은 최대 5만 행인데, 페이지마다 하는 2,000만 행 스캔 앞에서는 미미하기 때문입니다.

### 결론
조인과 집계가 있어 1회 실행 비용이 큰 쿼리에서는 OFFSET 여부보다 **쿼리를 몇 번 보내느냐**가 결정적입니다. 이 Job은 Cursor를 기본으로 사용합니다. OFFSET과 No-Offset의 차이는 인덱스로 페이지당 비용을 줄인 뒤(Step 4) 다시 비교합니다.

**구현 메모**
- Spring Batch의 `JdbcPagingItemReader`도 키셋 방식이지만, GROUP BY가 있으면 쿼리를 `SELECT * FROM (집계) WHERE 키 > ?`로 감싸서 **집계 후에** 필터링합니다. 그래서 키 조건을 집계 전 WHERE에 넣도록 직접 구현했습니다.
- No-Offset Reader는 마지막으로 **반환한** 키를 `ExecutionContext`에 저장합니다. 미리 가져온 버퍼 기준으로 저장하면, 재시작 시 처리하지 않은 행을 건너뛰게 됩니다.

---

## 3. Chunk size 비교

**조건**: 9월 1개월(1,513,693행), Cursor, 각 3회

### 원본 데이터 (집계 Step 시간, 초)

| 세트 | chunk 100 | chunk 1,000 | chunk 5,000 |
|---|---|---|---|
| 1 (exec 14~16) | 238.3 | 71.4 | 47.9 |
| 2 (exec 17~19) | 248.2 | 53.0 | 28.4 |
| 3 (exec 20~22) | 226.0 | 47.4 | 30.4 |

9회 모두 `read=1,513,693`, 커밋 횟수는 이론값과 같습니다(15,137 / 1,514 / 303).

### 결과

| chunk | 중앙값 | 커밋 횟수 | chunk 100 대비 |
|---|---|---|---|
| 100 | 238.3s | 15,137 | 1.0× |
| 1,000 | 53.0s | 1,514 | 4.5배 빠름 |
| **5,000** | **30.4s** | 303 | **7.8배 빠름** |

### 커밋당 고정 비용

| 구간 | 줄어든 시간 | 줄어든 커밋 | 커밋 1회당 |
|---|---|---|---|
| 100 → 1,000 | 185.3s | 13,623 | 약 13.6ms |
| 1,000 → 5,000 | 22.6s | 1,211 | 약 18.7ms |

두 구간이 비슷해서, 처리 시간은 **"고정 작업 약 26초 + 커밋 수 × 약 15ms"**로 설명됩니다. 커밋 비용에는 트랜잭션 커밋, `BATCH_STEP_EXECUTION`·`_CONTEXT` 갱신, Writer의 DB 왕복이 포함됩니다.

| chunk | 커밋 비용 추정 | 전체 대비 |
|---|---|---|
| 100 | 약 210s | 약 88% |
| 1,000 | 약 22s | 약 40% |
| 5,000 | 약 4.5s | 약 15% |

### 결론
- 1,000 → 5,000도 43% 단축으로, 1,000은 아직 최적점이 아니었습니다. 5,000 이상에서는 남은 커밋 비용이 약 4.5초라 수확 체감 구간입니다.
- chunk 5,000의 단점이 이 Job에서는 작습니다. chunk당 약 0.1초, `SalesRow` 5,000개로 메모리 1MB 미만, 실패 시 재처리 범위도 최대 5,000행입니다. 그래서 **기본값을 5,000으로 채택**했습니다.
- 행 단위 처리가 무겁거나 외부 호출이 있는 Job이라면 재처리 비용 때문에 더 작게 잡아야 합니다. 이 결론은 이 Job의 특성에서 나온 것입니다.
- 1세트가 느린 것은 캐시가 식은 상태였기 때문입니다. 2·3세트는 chunk 1,000이 47~53초, 5,000이 28~30초로 안정적입니다.

---

## 4. Partitioning

**조건**: 9월 1개월, Cursor, chunk 5,000, 각 3회

기간을 **날짜 단위**로 gridSize개로 나눠 파티션마다 별도 스레드에서 처리합니다. 날짜로 나누면 파티션끼리 같은 (판매자, 날짜) 키를 건드리지 않아서, 병렬 upsert 사이에 행 잠금 경합이나 누적값 충돌이 없습니다. `gridSize=1`은 파티션 하나가 전체 기간을 처리하는 단일 스레드 실행입니다.

### 원본 데이터 (Manager Step 시간, 초)

| 세트 | gridSize 1 | gridSize 2 | gridSize 4 | gridSize 8 |
|---|---|---|---|---|
| 1 (exec 23~26) | 35.7 | 35.2 | 30.0 | 16.5 |
| 2 (exec 27~30) | 24.6 | 18.0 | 16.8 | 12.0 |
| 3 (exec 31~34) | 23.8 | 22.5 | 28.2 | 15.3 |

12회 모두 `read=1,513,693`, 파티션별 처리 건수의 합이 전체와 일치합니다.

### 결과

| gridSize | 중앙값 | 단일 대비 | 이상적인 경우 |
|---|---|---|---|
| 1 | 24.6s | 1.0× | 1× |
| 2 | 22.5s | 1.1× | 2× |
| 4 | 28.2s | 0.9× | 4× |
| **8** | **15.3s** | **1.6×** | 8× |

gridSize 8은 세 세트 모두에서 가장 빨랐지만, 같은 세트 안의 gridSize 1 대비 1.6~2.2배에 그쳤습니다. gridSize 2와 4는 세트마다 들쭉날쭉해서 의미 있는 개선으로 보기 어렵습니다.

### 원인 분석 — 파티션별 기록
gridSize 8에서 4일을 담당한 파티션과 3일을 담당한 파티션의 시간을 비교했습니다.

| 실행 | 4일 파티션 (약 20.2만 행) | 3일 파티션 (약 15.1만 행) | 하루당 처리 | 4일 파티션 중 고정 비용 |
|---|---|---|---|---|
| exec 26 | 16.1s | 15.6s | 약 0.5s | 약 14.1s (**88%**) |
| exec 30 | 11.7s | 10.65s | 약 1.05s | 약 7.5s (**64%**) |
| exec 34 | 14.9s | 14.2s | 약 0.7s | 약 12.1s (**81%**) |

담당 일수가 줄어도 시간은 거의 줄지 않습니다. 파티션 시간의 **64~88%가 담당 일수와 무관한 고정 비용**입니다. 바로 파티션마다 따로 수행하는 **orders·order_item 전체 스캔**입니다. 파티션을 늘리면 처리 시간은 나뉘지만 전체 스캔은 파티션 수만큼 반복됩니다(gridSize 8이면 2.8GB 스캔이 동시에 8번). 나눌 수 없는 부분이 병목이라 병렬화 효과가 제한됩니다.

gridSize 2와 4의 편차는 동시 풀스캔 사이의 경합으로 보입니다. 메모리 6.9GB 환경에서 2.8GB 테이블을 동시에 여러 번 읽으며 캐시가 밀리고, DB 병렬 워커(최대 8개) 배정도 실행 시점마다 달라집니다.

### 결론
인덱스 없이 Partitioning을 적용하면 각 파티션이 전체 스캔을 중복 수행해 확장성이 제한됩니다. **병렬화보다 각 파티션이 자기 날짜 범위만 읽도록 하는 인덱스 설계가 선행되어야 합니다.** 그래서 기본값은 `gridSize=1`로 두고, 인덱스 추가 후 다시 측정합니다.

**구현 메모**
- Worker Step을 `@JobScope` 빈으로 두면, 파티션 스레드에는 Job 컨텍스트가 없어 프록시가 실제 Step을 찾지 못합니다. 그래서 Manager 안에서 일반 객체로 생성합니다. Reader는 `@StepScope`라 파티션 스레드마다 따로 생성됩니다.
- Reader는 `from`/`to`를 Job 파라미터가 아니라 파티션별 `stepExecutionContext`에서 읽습니다.
- 파티션 하나가 DB 연결 2개(커서 1 + chunk 트랜잭션 1)를 사용합니다. gridSize 8이면 16개로 HikariCP 풀(50) 안입니다.

---

## 5. 실패 후 재시작

**조건**: 9월 1개월, Cursor, chunk 5,000, gridSize 4, `run=70`

`failOnDate=2026-09-05`(non-identifying)를 주면 `FailureInjectingSalesDeltaProcessor`가 해당 날짜 행에서 예외를 던집니다. 9/5는 partition00(9/1~9/8)의 중간이라, 9/1~9/4를 처리한 chunk가 커밋된 뒤에 실패합니다.

`failOnDate`를 **non-identifying**(`,false`)으로 넘겨야 재시작할 때 이 값을 뺄 수 있습니다. identifying이면 값을 빼는 순간 다른 Job Instance가 되고, 그대로 두면 또 실패합니다.

### 실행

| execution | instance | 상태 | 파라미터 |
|---|---|---|---|
| 35 | **34** | FAILED | from, to, gridSize=4, reader=cursor, run=70, **failOnDate=2026-09-05 (non-identifying)** |
| 36 | **34** | COMPLETED | from, to, gridSize=4, reader=cursor, run=70 |

`failOnDate`를 빼도 같은 instance(34)로 묶여 **같은 작업의 재시작**으로 처리됐습니다.

### Step별 기록

| Step | 1차 (exec 35) | 2차 재시작 (exec 36) |
|---|---|---|
| deleteDailySalesSummaryStep | COMPLETED | 실행 안 함 |
| **partition00** (9/1~9/8) | **FAILED**: read 205,000 / **write 200,000** / commit 40 / **rollback 1** | **COMPLETED**: read **204,870** / commit 41 |
| partition01 (9/9~9/16) | COMPLETED: 403,473행 | 실행 안 함 |
| partition02 (9/17~9/23) | COMPLETED: 353,138행 | 실행 안 함 |
| partition03 (9/24~9/30) | COMPLETED: 352,212행 | 실행 안 함 |
| Manager | FAILED, 33.0s | COMPLETED, **19.9s** |

실패 원인은 메타데이터에 남습니다. partition00은 `FatalStepExecutionException: Unable to process chunk`, Manager는 `Partition handler returned an unsuccessful step`입니다.

### 검증

| 관찰 | 의미 |
|---|---|
| write 200,000 = 40 chunk × 5,000 | 9/1~9/4 데이터는 커밋 완료 |
| read 205,000, rollback 1 | 9/5를 만난 41번째 chunk(5,000행)는 롤백. 쓰기와 진행 위치 모두 반영 안 됨 |
| 재시작 시 partition00만 실행 | 성공한 파티션과 삭제 Step은 다시 실행되지 않음 (재시작 단위 = 파티션) |
| 재시작 read 204,870 | 처음부터가 아니라 커밋된 200,000행 다음부터 이어서 읽음 |
| 200,000 + 204,870 = **404,870** | partition00 전체 행 수(exec 25의 partition00과 동일)와 일치 |
| 404,870 + 403,473 + 353,138 + 352,212 = **1,513,693** | 전체 대상 행 수와 일치 |
| 원본 SQL 집계와 비교 | 30,000행, **불일치 0** |

Writer는 누적 upsert(`+=`)지만 재시작해도 중복으로 더해지지 않습니다. Writer의 쓰기와 Reader의 진행 위치(`ExecutionContext`)가 **같은 chunk 트랜잭션에서 함께 커밋되고 함께 롤백되기** 때문입니다.

소량 데이터 검증 DB에서는 Cursor·JPA·No-Offset 세 Reader 모두 같은 방식으로 실패와 재시작을 확인했습니다.

---

## 다음 단계 (Step 4)

| 대상 | 계획 |
|---|---|
| 인덱스 | `orders(order_time)`, `order_item(order_id)` 설계 후 Reader 쿼리 `EXPLAIN ANALYZE` 전후 비교 |
| Reader 2라운드 | 인덱스 추가 후 OFFSET vs No-Offset 재비교. OFFSET이 뒤 페이지로 갈수록 느려지는지 확인 |
| Partitioning 2라운드 | 파티션이 자기 범위만 읽게 된 뒤 gridSize별 확장성 재측정 |

튜닝 전 기준값:
- 9월 Reader 쿼리 단독 **14.1s** (Parallel Seq Scan × 2 + Hash Join)
- 하루 페이지 쿼리 **1.6s**
- 9월 원본 직접 집계(검증 쿼리) **31.8s**

---

## 부록

### A. 실행 명령

Active profiles는 `local,bench`입니다. 같은 파라미터로 성공한 Job은 다시 실행되지 않으므로 실행마다 `run` 값을 바꿉니다.

```
--spring.batch.job.enabled=true --spring.batch.job.name=dailySalesSummaryJob from=2026-09-01,java.time.LocalDate to=2026-09-30,java.time.LocalDate reader=cursor chunkSize=5000,java.lang.Long gridSize=1,java.lang.Long run=100,java.lang.Long
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `from`, `to` | (필수) | 집계 기간, 양 끝 포함 |
| `reader` | `cursor` | `cursor` / `jpa` / `nooffset` |
| `chunkSize` | 5000 | chunk 크기 (페이징 Reader는 페이지 크기도 동일) |
| `gridSize` | 1 | 파티션 수 |
| `failOnDate` | 없음 | 재시작 시연용. `failOnDate=2026-09-05,java.lang.String,false`처럼 non-identifying으로 전달 |
| `run` | 없음 | 같은 조건을 반복 실행할 때 구분용 |

재시작은 `failOnDate`만 빼고 나머지 인자를 그대로 두고 다시 실행합니다.

### B. 정확성 검증 쿼리

Job 결과와 원본 직접 집계를 양방향으로 비교합니다. `missing_or_diff`, `extra_or_diff`가 모두 0이면 일치입니다.

```sql
WITH expected AS (
  SELECT i.seller_id, CAST(o.order_time AS date) AS sales_date,
         count(DISTINCT o.id) FILTER (WHERE o.status = 'PAID')                            AS order_count,
         COALESCE(sum(oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)                   AS item_quantity,
         COALESCE(sum(oi.order_price * oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)  AS sales_amount,
         count(DISTINCT o.id) FILTER (WHERE o.status = 'CANCEL')                          AS cancel_count,
         COALESCE(sum(oi.order_price * oi.quantity) FILTER (WHERE o.status = 'CANCEL'), 0) AS cancel_amount
  FROM orders o
  JOIN order_item oi ON oi.order_id = o.id
  JOIN item i ON i.id = oi.item_id
  WHERE o.order_time >= '2026-09-01' AND o.order_time < '2026-10-01'
    AND o.status IN ('PAID', 'CANCEL')
  GROUP BY 1, 2
), actual AS (
  SELECT seller_id, sales_date, order_count, item_quantity, sales_amount, cancel_count, cancel_amount
  FROM daily_sales_summary
  WHERE sales_date BETWEEN '2026-09-01' AND '2026-09-30'
)
SELECT (SELECT count(*) FROM expected) AS expected_rows,
       (SELECT count(*) FROM actual)   AS actual_rows,
       (SELECT count(*) FROM (SELECT * FROM expected EXCEPT SELECT * FROM actual) x) AS missing_or_diff,
       (SELECT count(*) FROM (SELECT * FROM actual EXCEPT SELECT * FROM expected) y) AS extra_or_diff;
```

### C. 실행 기록 조회 쿼리

파라미터와 Step별 처리 건수, 소요 시간을 한 번에 봅니다.

```sql
SELECT e.job_execution_id AS exec, e.job_instance_id AS instance, e.status AS job_status,
       (SELECT string_agg(parameter_name || '=' || parameter_value
                          || CASE WHEN identifying = 'N' THEN ' (non-id)' ELSE '' END, ', ' ORDER BY parameter_name)
        FROM batch_job_execution_params p WHERE p.job_execution_id = e.job_execution_id) AS params,
       s.step_name, s.status, s.read_count, s.write_count, s.commit_count, s.rollback_count,
       round(EXTRACT(epoch FROM s.end_time - s.start_time)::numeric, 1) AS step_sec
FROM batch_job_execution e
JOIN batch_step_execution s USING (job_execution_id)
WHERE s.step_name <> 'deleteDailySalesSummaryStep'
ORDER BY e.job_execution_id, s.step_execution_id;
```

특정 실험만 보려면 `WHERE` 절에 `run` 파라미터 조건을 추가합니다.

```sql
AND e.job_execution_id IN (
  SELECT job_execution_id FROM batch_job_execution_params
  WHERE parameter_name = 'run' AND parameter_value = '70'
)
```
