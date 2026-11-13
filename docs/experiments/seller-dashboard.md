# Step 3. 판매자 대시보드 — 원본 집계 → 집계 테이블 → Redis 캐시 실험 기록

판매자 한 명의 30일 매출을 보여주는 대시보드 API를 만들고, 같은 결과를 **원본 직접 집계 / 집계 테이블 / 집계 테이블 + Redis 캐시** 세 방식으로 제공하며 HTTP 부하에서 응답 시간과 **DB 작업량**을 비교한 기록입니다. 측정 중 대시보드와 무관한 문제 두 가지(JWT 필터의 회원 테이블 전체 스캔, PostgreSQL 공유 메모리 부족)를 발견해 함께 고쳤고, 마지막으로 배치 갱신 후 캐시가 옛 값을 주는 문제를 재현하고 해결했습니다.

## 요약

| 방식 | 처리 가능 부하 | p50 | p95 | DB 트랜잭션/요청 | 읽은 블록/요청 | 스캔 행/요청 |
|---|---|---|---|---|---|---|
| 원본 직접 집계 | 초당 2.5~12건에서 포화 | **7.7~26.9s** | 11.6~34.0s | – | – | – |
| 집계 테이블 | 초당 200건 여유 | 7.1ms | 13.7ms | 1.96 | 59 | 1,008 |
| 집계 테이블 + 이메일 인덱스 | 초당 200건 여유 | 7.4ms | 13.4ms | 1.99 | 39.7 | **33.9** |
| **+ Redis 캐시** | 초당 200건 여유 | **5.9ms** | **10.3ms** | **1.17** | **9.4** | **6.8** |

**결론**
1. **원본 직접 집계는 API로 쓸 수 없습니다.** 단일 쿼리 265ms(Step 4)가 동시 부하에서는 수 초~수십 초가 됐습니다. 집계 테이블은 같은 답(양방향 `EXCEPT` 0행)을 **약 1,000배 빠르게** 냅니다.
2. **캐시의 효과는 응답 시간보다 DB 부하에서 컸습니다.** 히트율 83%에서 응답 시간은 20% 줄었지만 DB 트랜잭션은 41%, 읽은 블록은 76% 줄었습니다. 집계 테이블 쿼리는 원래 1ms 미만이라, 응답 시간의 대부분은 DB 밖(JWT 검증, JSON 변환, 네트워크)에서 쓰였기 때문입니다.
3. **캐시를 붙인 뒤 남은 DB 비용은 JWT 필터의 회원 조회**였습니다. 그 조회가 이메일 인덱스 없이 회원 테이블 전체를 스캔하고 있어 함께 고쳤습니다.
4. **배치가 집계 테이블을 갱신하면 캐시를 비워야 합니다.** 그렇지 않으면 TTL(10분) 동안 옛 매출이 보입니다.

> ⚠️ 각 조건 1회 측정입니다. 방식 간 차이가 수십~수천 배라 결론에는 영향이 없지만, 집계 테이블과 캐시의 응답 시간 차이(7.4 vs 5.9ms)는 반복 측정으로 확정해야 합니다.

---

## 실험 환경

| 항목 | 값 |
|---|---|
| 데이터 | `shop` DB: 주문 10,000,268 / 주문상품 19,997,712 / 상품 100,000 / 판매자 1,000 / 회원 1,002. 집계 테이블은 **2026-09-01 ~ 09-30, 30,000행**(판매자 1,000 × 30일) |
| DB | PostgreSQL 16.14 (Docker), `shared_buffers` 128MB, **`shm_size: 1g`** (측정 도중 64MB에서 변경, 5절 참고) |
| 애플리케이션 | Java 21, Spring Boot 4.0.2, 프로필 `local,bench` (`ddl-auto: none`, 조회만 하므로 1,000만 건 DB를 읽기 전용으로 사용), HikariCP 50 |
| 부하 | k6 [`loadtest/dashboard-scenario.js`](../../loadtest/dashboard-scenario.js), `constant-arrival-rate` 30초, 판매자 id 1~1,000 무작위, 기간 9/1~9/30 고정 |
| 인증 | 측정용 관리자 계정으로 `setup()`에서 로그인. 로그인 실패 시 측정을 시작하지 않고 중단 |

### API
```
GET /api/admin/sellers/{sellerId}/dashboard?from=2026-09-01&to=2026-09-30   (ADMIN 전용)
→ { sellerId, from, to, days: [ {salesDate, orderCount, itemQuantity, salesAmount, cancelCount, cancelAmount} ×30 ],
    totalSalesAmount, totalOrderCount }
```
- 조회 기간은 최대 92일로 제한합니다.
- 판매자와 회원이 연결되어 있지 않아(`Seller`에 회원 연관 없음) 이번에는 ADMIN 전용으로 만들었습니다. 판매자 본인 조회는 후속 과제입니다.

### 방식 전환
`SalesSource` 인터페이스의 구현체 세 개를 `dashboard.source` 설정값(`raw` / `summary` / `cache`)으로 하나만 빈으로 등록합니다. 기본값은 `summary`입니다.

### 측정 지표
- **k6**: p50 / p95 / p99, 실제 처리량, 처리하지 못한 요청(`dropped_iterations`), 응답 검사(200이고 30일치인지)
- **DB 작업량**: 측정 전후 `pg_stat_database`의 차이를 요청 수로 나눔
  - `xact_commit` → 트랜잭션/요청
  - `blks_hit + blks_read` → 읽은 블록/요청 (`blks_read`는 디스크 읽기)
  - `tup_returned` → 스캔한 행/요청
- **캐시**: Redis `INFO stats`의 `keyspace_hits` / `keyspace_misses` 차이, 측정 후 캐시 키 개수

---

## 1. 정합성 검증: 두 방식이 같은 답을 내는가

원본 직접 집계 쿼리를 배치와 **같은 기준**(주문 × 판매자 단위, `PAID`는 매출, `CANCEL`은 취소)으로 작성했습니다.

```sql
SELECT CAST(o.order_time AS date) AS sales_date,
       COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'PAID')                             AS order_count,
       COALESCE(SUM(oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)                    AS item_quantity,
       COALESCE(SUM(oi.order_price * oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)   AS sales_amount,
       COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'CANCEL')                           AS cancel_count,
       COALESCE(SUM(oi.order_price * oi.quantity) FILTER (WHERE o.status = 'CANCEL'), 0) AS cancel_amount
FROM item i
JOIN order_item oi ON oi.item_id = i.id
JOIN orders o      ON o.id = oi.order_id
WHERE i.seller_id = :sellerId
  AND o.order_time >= :from AND o.order_time < :toExclusive
  AND o.status IN ('PAID', 'CANCEL')
GROUP BY CAST(o.order_time AS date)
ORDER BY sales_date
```
- `COUNT(DISTINCT o.id)`: 주문 하나에 이 판매자 상품이 여러 개면 행이 여러 개라 중복 제거
- 날짜 조건은 `>=`, `<`로 컬럼을 가공하지 않아 `orders(order_time)` 인덱스를 그대로 사용

판매자 500의 9월 30일치를 집계 테이블과 양방향 `EXCEPT`로 비교한 결과 **0행(완전 일치)**이었습니다.

---

## 2. 원본 직접 집계 vs 집계 테이블

### 원본 직접 집계 (`shm_size: 1g` 적용 후)
| 조건 | 목표 | 실제 처리량 | p50 | p95 | 처리 못 한 요청 | 실패 |
|---|---|---|---|---|---|---|
| 컨테이너 재생성 직후 (콜드) | 5건/s | **2.5건/s** | 26.9s | 34.0s | 50 | 0 |
| 이어서 (웜) | 20건/s | 12.3건/s | 7.7s | 11.6s | 107 | 0 |

### 집계 테이블
| 목표 | 실제 처리량 | p50 | p95 | p99 | max | 동시 실행 VU 최대 |
|---|---|---|---|---|---|---|
| 5건/s | 5.0건/s | 9.5ms | 11.9ms | 20.9ms | 80ms | – |
| 20건/s | 20.0건/s | 7.8ms | 10.9ms | 17.6ms | 73ms | 1 |
| 200건/s | **199.4건/s** | 7.1ms | 13.7ms | 17.1ms | 72ms | **7** |

**요청 1건당 DB 작업량 (집계 테이블)**
| 부하 | 요청 | 트랜잭션 | 읽은 블록 | 디스크 읽기(전체) | 스캔 행 |
|---|---|---|---|---|---|
| 20건/s | 602 | 2.0 | 60 | 21블록 | 1,034 |
| 200건/s | 6,001 | 1.96 | 59 | 2블록 | 1,008 |

### 해석: 단일 쿼리 265ms가 왜 API에서는 수십 초가 되나
Step 4의 265ms는 **판매자 한 명을 반복 조회한, 캐시가 데워진 단일 쿼리**였습니다. 부하 조건은 다릅니다.

| 조건 | Step 4 측정 | 이번 부하 |
|---|---|---|
| 판매자 | 500번 고정 | 1~1,000 무작위 |
| 필요한 데이터 | 한 판매자 몫 | 9월 주문 전체(주문 약 150만, 주문상품 약 300만 행) |
| 메모리 캐시 | 다 들어감 | `shared_buffers` 128MB에 안 들어가 계속 디스크 읽기 |
| 동시 실행 | 1개 | 최대 50개(커넥션 풀)가 디스크·CPU를 나눠 씀 |

콜드 상태에서 처리량 2.5건/s × 평균 응답 25초 ≈ **동시에 60여 건**이 몰려 있었습니다(커넥션 50 + 대기). 쿼리 하나가 느린 게 아니라, **무거운 쿼리 수십 개가 자원을 나눠 쓰며 모두 느려진 것**입니다. 부하보다 **캐시 상태**가 결과를 좌우해, 부하가 4배인 웜 측정이 오히려 빨랐습니다.

---

## 3. 발견: JWT 필터가 매 요청마다 회원 테이블 전체를 스캔

집계 테이블의 응답은 30행인데 **요청당 1,000행을 스캔**하고 있었습니다. 1,000은 회원 수(1,002)와 같았습니다.

```java
// JwtAuthenticationFilter: 모든 인증 요청마다 실행
Member member = memberRepository.findByEmail(email).orElse(null);

// Member: unique 제약이 phone에만 있고 email에는 인덱스가 없었음
@Column(nullable = false, unique = true) private String phone;
private String email;
```
```
EXPLAIN ANALYZE SELECT * FROM member WHERE email = '...';
Seq Scan on member  (actual time=0.609..0.610 rows=1 loops=1)
  Rows Removed by Filter: 1001
```

**수정**
- 중복 이메일 0건을 확인한 뒤 `CREATE UNIQUE INDEX CONCURRENTLY uk_member_email ON member (email)`
- 엔티티에 `@Column(nullable = false, unique = true)` 추가 (운영 `ddl-auto: update`에도 반영)

| 요청 1건당 (집계 테이블, 200건/s) | 인덱스 전 | 인덱스 후 |
|---|---|---|
| 스캔한 행 | 1,008 | **33.9** (-97%) |
| 읽은 블록 | 59 | 39.7 (-33%) |
| p50 / p95 | 7.1 / 13.7ms | 7.4 / 13.4ms |

- **응답 시간은 그대로**: 회원 1,000명이라 순차 스캔도 1ms 미만이었습니다. 이 수정의 가치는 **회원 수에 비례하던 모든 인증 API의 비용을 없앤 것**입니다. 회원 100만 명이면 요청마다 100만 행을 스캔했을 것입니다.
- **정합성**: DB에 unique 제약이 없어 같은 이메일로 동시 가입하면 중복 계정이 생길 수 있었습니다. 로그인이 이메일 기준이라 이후 조회가 깨집니다.
- 인덱스 후 측정은 앱 재시작 직후 워밍업 없이 진행해 p99(54.9ms)·max(397ms)가 튀었습니다.

---

## 4. Redis Cache-Aside

### 구조
```
SellerDashboardService → CachedSummarySalesSource → DailySalesCacheLoader (@Cacheable "sellerDashboard")
                                                        ├─ 히트: Redis에서 반환
                                                        └─ 미스: 집계 테이블 조회 → Redis 저장 (TTL 10분)
```
- 캐시 키: `판매자:시작일:종료일`
- `@Cacheable`은 별도 빈(`DailySalesCacheLoader`)에 둠. 같은 클래스 안에서 호출하면 프록시를 거치지 않아 캐시가 동작하지 않음(자기 호출)
- 대시보드 캐시만 **타입을 지정한 직렬화기**(`JacksonJsonRedisSerializer<CachedDailySales>`)를 씀. 이유는 6절 참고

### 결과 (200건/s, 측정 전 캐시 비움)
| 항목 | 값 |
|---|---|
| Redis 히트 / 미스 | 5,005 / 995 → **히트율 83.4%** |
| 저장된 캐시 키 | 995개 (미스 수와 일치) |
| p50 / p95 / p99 | 5.9 / 10.3 / 21.7ms |

히트율은 가설(약 83%)과 맞았습니다. 30초 동안 판매자 1,000명 중 무작위로 6,000번 요청해, 서로 다른 판매자 995명이 한 번씩 미스였고 나머지는 히트였습니다.

### 캐시 적용 효과 (이메일 인덱스 적용 후 대비)
| 요청 1건당 | 집계 테이블 | + 캐시 | 변화 |
|---|---|---|---|
| p50 | 7.4ms | 5.9ms | **-20%** |
| DB 트랜잭션 | 1.99 | 1.17 | **-41%** |
| 읽은 블록 | 39.7 | 9.4 | **-76%** |
| 스캔 행 | 33.9 | 6.8 | **-80%** |

**숫자로 맞춰 본 요청당 비용**
```
트랜잭션: 모든 요청 1개(JWT 회원 조회) + 미스일 때만 1개(대시보드) = 6,000 + 995 = 6,995 ≈ 측정 7,043
블록:    회원 조회(인덱스) 약 4블록 × 6,000 + 대시보드 조회 약 31블록 × 995 ≈ 55,000 ≈ 측정 56,669
```
캐시 적용 후 **요청마다 남는 DB 작업은 JWT 필터의 회원 조회 하나**입니다. 다음 개선 후보는 JWT에 회원 id·역할을 담아 조회를 생략하거나(권한 변경 반영 지연), 인증 정보를 짧은 TTL로 캐시하는 것입니다.

---

## 5. 발견: 원본 집계 동시 실행 시 PostgreSQL 공유 메모리 부족

`shm_size` 변경 전, 원본 직접 집계를 초당 20건으로 측정하자 요청의 37~43%가 0.5초 안팎에 실패했습니다.

| 회차 (shm 64MB) | 성공률 | 처리량 | p50 | p95 |
|---|---|---|---|---|
| 1 (콜드) | 100% | 8.4건/s | 17.3s | 22.0s |
| 2 | 57% | 18.8건/s | 1.2s | 5.3s |
| 3 | 63% | 19.5건/s | 1.1s | 2.0s |

```
ERROR: could not resize shared memory segment "/PostgreSQL.xxx" to 113440 bytes: No space left on device
```
- **원인**: 원본 집계 쿼리를 PostgreSQL이 병렬 해시 조인으로 실행하면, 작업자들이 `/dev/shm`에 해시 테이블을 공유합니다. Docker 컨테이너의 기본 `/dev/shm`은 64MB라, 이런 쿼리가 동시에 여러 개 돌면 공간이 모자라 쿼리가 즉시 실패합니다.
- **1회차는 왜 안 났나 (추정)**: 디스크 대기로 병렬 해시 단계에 동시에 들어간 쿼리가 적었던 것으로 보입니다.
- **수정**: `docker-compose.yml`의 postgres에 `shm_size: 1g`. 이후 실패 0%
- **운영 영향**: `docker-compose.prod.yml`도 PostgreSQL을 Docker로 띄워 같은 위험이 있습니다. 운영 서버(2GB)는 1GB를 줄 수 없어, 메모리 예산 안에서 늘리거나 `max_parallel_workers_per_gather`를 낮춰야 합니다. 대시보드가 집계 테이블을 쓰면 이런 무거운 쿼리 자체가 사라집니다.

---

## 6. 발견: 캐시 조회가 두 번째 요청부터 500

첫 요청은 200, 같은 판매자의 두 번째 요청부터 500이 났습니다.

```
SerializationException: Could not read JSON: Unexpected token (START_OBJECT), expected VALUE_STRING:
need ... type id (for subtype of java.lang.Object)
```
- 기존 `RedisConfig`는 값과 함께 **클래스 정보**를 JSON에 저장하고, 꺼낼 때 그 정보로 복원합니다. 이 설정은 **final 클래스에는 클래스 정보를 붙이지 않습니다.**
- 캐시에 넣은 리스트는 `Stream.toList()`로 만든 것이라 실제 클래스가 JDK 내부의 `ImmutableCollections$ListN`(final)이고, 원소 `DailySalesResponse`도 record(final)라 **겉과 안 모두 클래스 정보 없이 저장**됐습니다.
- 저장은 성공하고 꺼낼 때만 실패해서, **첫 요청 테스트로는 발견되지 않는** 버그였습니다.

**수정**: 대시보드 캐시에만 타입을 지정한 직렬화기를 씁니다. 값은 `CachedDailySales(List<DailySalesResponse> days)` record로 감쌉니다. 기존 상품 캐시 설정은 그대로입니다.
```java
RedisCacheConfiguration dashboardConfig = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(CachedDailySales.class)));
```

**원인 파악이 늦었던 이유**: 공통 예외 처리기가 `Exception`을 받아 "서버 오류가 발생했습니다."만 응답하고 **로그를 남기지 않았습니다.** 콘솔에 아무것도 없어 원인을 볼 수 없었습니다. `log.error("처리되지 않은 예외", e)`를 추가했습니다. 응답에는 지금처럼 일반 메시지만 줍니다.

---

## 7. 캐시 무효화: 배치 갱신 후 옛 매출이 보이는 문제

### 재현 (주문 데이터는 건드리지 않음)
1. 집계 테이블의 판매자 500, 9/15 매출을 +1,000,000으로 조작 (배치 전 옛 값이라고 가정)
2. API 호출 → 조작된 값이 캐시에 저장됨
3. 9/15만 배치 재실행 → 원본 주문에서 다시 집계해 DB가 올바른 값으로 복구됨
4. API 다시 호출

| 판매자 500, 9/15 매출 | 수정 전 | 수정 후 |
|---|---|---|
| API 호출 1번 (조작값 캐시) | 2,511,100 | 2,511,100 |
| 배치 후 DB 값 | 1,511,100 | 1,511,100 |
| **배치 후 API 호출** | **2,511,100 ❌** | **1,511,100 ✅** |
| 30일 합계 | 73,242,000 (100만 원 과다) | 72,242,000 |

수정 전에는 DB가 고쳐졌는데도 TTL(10분)이 지날 때까지 옛 매출을 보여줬습니다.

### 해결
```java
@Component
public class DashboardCacheEvictListener implements JobExecutionListener {
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) return;   // 실패 시 캐시 유지
        cacheManager.getCache("sellerDashboard").clear();
    }
}
```
```java
// RedisConfig: clear() 시 KEYS 대신 SCAN으로 키를 찾음
RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(1000))
```

| 설계 판단 | 이유 |
|---|---|
| 배치 성공 시에만 비움 | 실패한 배치는 집계 테이블이 일부만 갱신된 상태일 수 있음. 이때는 배치 전 캐시가 오히려 정확 |
| 해당 기간만이 아니라 대시보드 캐시 전체 삭제 | 키가 `판매자:시작일:종료일`이라 임의 기간이 섞여 갱신된 날짜를 포함한 키만 골라내기 어려움. 배치는 하루 한 번이라 전체 삭제 비용이 작음 |
| 배치와 API가 다른 프로세스여도 동작 | 캐시가 공유 Redis에 있기 때문. 앱 메모리 캐시였다면 배치가 API 서버의 캐시를 지울 수 없음 |
| `KEYS` 대신 `SCAN` | `KEYS`는 Redis 전체 키를 훑는 동안 다른 명령을 모두 멈춤. 같은 Redis를 쓰는 쿠폰 발급·결제 실패 집계가 막힘 |

---

## 8. 측정 중 겪은 함정

| 함정 | 증상 | 교훈 |
|---|---|---|
| 측정용 관리자 로그인 실패 | 요청 601건이 모두 인증 거부인데 응답 시간 2.65ms로 "빨라 보임" | `setup()`에서 로그인 실패 시 측정을 중단하도록 변경 |
| 캐시 방식 구현체가 캐시 로더를 호출하지 않음 | k6는 200·100% 성공이었지만 Redis 히트·미스 0, 캐시 키 0 | 측정 전 "캐시 키가 실제로 생기는지" 확인하는 단계를 추가 |
| `@ConditionalOnProperty` 속성 이름 오타 (`dashboard.summary`) | 기동 실패 (`SalesSource` 빈 없음) | 속성 이름을 상수로 공유하면 컴파일 단계에서 걸림 |
| 배치 `run` 파라미터 재사용 | `JobInstanceAlreadyCompleteException` | 같은 날짜를 다시 집계할 때 `run` 값을 바꿔야 함 |

결제 측정의 URL 오타, Redis 문지기의 상수 버그와 같은 종류로, **측정이 성공으로 보여도 측정 대상이 실제로 동작했는지** 따로 확인해야 했습니다.

---

## 한계와 후속 과제
- **반복 측정**: 각 조건 1회. 집계 테이블과 캐시의 응답 시간 차이는 반복 측정으로 확정 필요
- **원본 집계의 DB 작업량**: 원본 직접 집계는 `pg_stat_database` 전후 값을 기록하지 않아 요청당 블록 수 비교가 없음
- **배치 실행 중 부분 데이터**: 배치가 "그날 데이터 삭제 → 재집계"를 서로 다른 Step에서 해, 그 사이 요청은 비거나 일부만 있는 값을 받을 수 있음(완료 후 캐시 삭제로 바로잡힘)
- **캐시 스탬피드**: 배치 완료 후 캐시를 전부 비운 직후 요청이 몰리면 모두 DB로 감. `@Cacheable(sync = true)` 등으로 대응 예정
- **접근 패턴**: 판매자를 균등 무작위로 요청했음. 실제로는 판매자가 자기 대시보드를 반복 조회해 히트율이 더 높을 것
- **ADMIN 권한 규칙**: `/api/admin/**`에 ADMIN 규칙을 추가함. 일반 회원 토큰으로 403이 나오는지는 별도 확인 필요
- **판매자 본인 조회**: 판매자와 회원 연결 필요
- **JWT 필터 회원 조회**: 캐시 적용 후 남은 유일한 요청당 DB 작업
