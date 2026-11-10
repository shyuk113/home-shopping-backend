# 주문 목록 조회 N+1 — 실험 기록

회원 주문 목록 API(`GET /api/orders`)가 **요청 한 번에 쿼리 약 28,000개**를 실행하는 N+1 문제를 1,000만 건 데이터에서 재현하고, 대안 5가지를 같은 측정 코드로 비교한 기록입니다. Step 4에서 인덱스를 설계했지만 이 API는 여전히 19초가 걸렸고, 원인은 쿼리 하나의 속도가 아니라 **쿼리 횟수**였습니다.

## 요약

| 방식 | 반환 건수 | 쿼리 수 | 응답 시간 (중앙값) | 기준 대비 |
|---|---|---|---|---|
| 기존 (지연 로딩) | 10,128 | 28,639 | 19,347ms | 1배 |
| `default_batch_fetch_size: 100` | 10,128 | 316 | 708ms | 27배 |
| `default_batch_fetch_size: 1000` | 10,128 | 33 | 467ms | 41배 |
| fetch join | 10,128 | 1 | 384ms | 50배 |
| DTO 직접 조회 | 10,128 | 1 | 173ms | 112배 |
| **페이징 20건 + batch 100 (채택)** | 20 | **4** | **20ms** | **약 970배** |

**비용 분해** — 쿼리 수를 줄이는 것만으로는 끝나지 않았습니다.

```
19.3s → 0.47s : 왕복 횟수 (N+1)               → batch size / fetch join으로 제거
0.38s → 0.17s : 엔티티 생성 · 영속성 컨텍스트  → DTO 조회로 제거
0.17s         : 1만 건 자체의 전송 · 변환       → 페이징으로만 줄일 수 있음
```

**결론**: 이 API의 근본 문제는 N+1과 **회원의 전체 주문(약 1만 건)을 한 번에 반환하는 설계** 두 가지였습니다. 페이징으로 반환량을 500분의 1로 줄이면 엔티티 생성 비용도 무시할 수준이 되므로, 가장 빠른 DTO 조회 대신 코드가 가장 단순한 **페이징 + `default_batch_fetch_size`**를 채택했습니다.

---

## 실험 환경

| 항목 | 값 |
|---|---|
| 데이터 | 주문 10,000,268 / 주문상품 19,997,712 / 상품 100,000 / 회원 1,000 (회원당 주문 약 1만 건, 균등 분포) |
| DB | PostgreSQL 16 (Docker, `localhost:5433`), Step 4 인덱스 적용 상태 |
| 애플리케이션 | Java 21, Spring Boot 4.0.2, Hibernate 7.2.1, 프로필 `local,bench` |
| 대상 회원 | 1000 (주문 10,128건), 711 (주문 10,331건, 최다) |

### 측정 방법
- **측정 코드**: [`OrderQueryBenchTest`](../../src/test/java/com/shop/backend/order/application/OrderQueryBenchTest.java)에서 `OrderService.getOrders()`를 직접 호출해 HTTP·JWT 인증 비용을 제외하고 조회 로직만 측정
- **쿼리 수**: Hibernate `Statistics.getPrepareStatementCount()`. 매 회 직전에 `clear()`
- **시간**: `System.nanoTime()`으로 서비스 호출 전체(쿼리 + 엔티티 생성 + DTO 변환)
- **반복**: 대안 1~4는 워밍업 1회 + 측정 5회, 페이징은 워밍업 3회 + 측정 7회. 모두 **중앙값**
- **격리**: 매 호출이 새 트랜잭션이라 1차 캐시가 비워진 상태에서 시작. 대안을 바꿀 때마다 다른 대안의 설정은 제거
- **데이터 보호**: `@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none", ...)`로 프로필 순서와 무관하게 스키마 재생성 방지

```bash
./gradlew test --tests "com.shop.backend.order.application.OrderQueryBenchTest" -Pbench --rerun-tasks -i
```
`-Pbench` 없이 실행하면 `@EnabledIfSystemProperty`로 건너뛰므로 일반 테스트·CI에는 영향이 없습니다.

---

## 1. 기준값: N+1 재현

### 원인 코드
```java
// OrderService (기존)
return orderRepository.findByMemberId(memberId).stream()
    .map(OrderResponse::from)           // 주문마다 order.getOrderItems() → 지연 로딩
    .toList();

// OrderItemResponse.from
orderItem.getItem().getId(),            // 프록시가 id를 이미 가지고 있어 쿼리 없음
orderItem.getItem().getName(),          // 여기서 상품 지연 로딩
```

### 결과
| 회원 | 주문 수 | 쿼리 수 | 중앙값 | 최소 / 최대 |
|---|---|---|---|---|
| 1000 | 10,128 | 28,639 | 19,347ms | 19,168 / 19,567ms |
| 711 | 10,331 | 28,924 | 19,728ms | 19,677 / 19,993ms |

**쿼리 분해 (회원 1000)**: orders 1 + order_item 10,128 (주문마다) + item 18,510 (주문상품마다) = 28,639

- 측정 전 가설(약 28,000개)과 일치. 주문상품 약 2만 개가 상품 10만 개에 흩어져 있어, 1차 캐시로 재사용된 상품은 약 1,700개뿐
- 쿼리당 평균 **0.68ms**. 개별 쿼리는 PK·FK 인덱스 조회로 이미 빠름 → **인덱스로는 해결할 수 없고 왕복 횟수를 줄여야 함**
- 쿼리 수가 주문 수에 비례 (10,128 → 28,639, 10,331 → 28,924). 앱과 DB가 다른 서버라면 왕복 시간이 늘어나는 만큼 2.8만 배로 불어남

### 회귀 테스트
[`OrderQueryCountTest`](../../src/test/java/com/shop/backend/order/application/OrderQueryCountTest.java)는 H2에서 주문 20건(주문마다 다른 상품)을 만들고 조회 쿼리 수가 4개 이하인지 검증합니다. 기존 코드에서는 41개(1 + 20 + 20)로 실패하고, 수정 후에는 4개로 통과합니다.

---

## 2. `default_batch_fetch_size`

지연 로딩 시 아직 초기화되지 않은 프록시·컬렉션을 모아 한 번에 조회합니다. PostgreSQL에서 Hibernate는 `IN (?, ?, …)` 대신 배열 파라미터 하나(`= any(?)`)를 써서 크기를 키워도 SQL 길이·파라미터 수 제한이 없습니다.

| 배치 크기 | 회원 1000 쿼리 / 중앙값 | 회원 711 쿼리 / 중앙값 |
|---|---|---|
| 100 | 316 / 708ms | 319 / 695ms |
| 1000 | 33 / 467ms | 33 / 439ms |

### 예측(289개)보다 쿼리가 많았던 이유
배치는 "앞으로 필요할 프록시 전체"가 아니라 **그 시점에 영속성 컨텍스트에 등록된 프록시**로만 만들어집니다.

```
order_item 배치 1회 (주문 1,000건) → 주문상품 약 2,000개 → 상품 프록시 약 1,850개 등록
  → item 배치: 1,000개 + 850개 = 2번   (1.85번이 아니라 2번)

batch 1000: 1 + order_item 11 + item (10배치 × 2 + 마지막 1) = 33   ← 실측 33과 일치
```
batch 100에서는 주문당 상품 수(1~3개)가 배치마다 달라 일부 배치에서 item 쿼리가 3번 나간 것으로 추정됩니다(316 vs 모델 약 306). SQL 로그로는 확인하지 않았습니다.

### 시간은 쿼리 수만큼 줄지 않음
| 구간 | 쿼리 수 | 시간 |
|---|---|---|
| 없음 → 100 | 91분의 1 | 27분의 1 |
| 100 → 1000 | 10분의 1 | 34% 감소 |

기준값의 쿼리당 0.68ms로 왕복 비용을 빼면 두 경우 모두 **약 450ms가 남습니다**. 이는 행 약 4만 개의 전송, 엔티티 생성, 영속성 컨텍스트 등록, DTO 변환 비용으로, 반환 데이터 양에 비례합니다.

---

## 3. fetch join

```java
@Query("""
    select o from Order o
    join fetch o.orderItems oi
    join fetch oi.item
    where o.member.id = :memberId
    """)
List<Order> findWithItemsByMemberId(@Param("memberId") Long memberId);
```

| 회원 | 쿼리 수 | 중앙값 | 최소 / 최대 |
|---|---|---|---|
| 1000 | 1 | 384ms | 338 / 566ms |
| 711 | 1 | 311ms | 263 / 364ms |

- 쿼리 1개, DB 반환 행 약 2만 개. Hibernate 6부터 컬렉션 fetch join의 중복 루트를 자동 제거해 `distinct` 없이 주문 10,128건
- batch 1000(467ms)과의 차이는 18%. 남은 비용이 왕복이 아니라 데이터 양에서 온다는 2절 분석과 일치
- 회차마다 시간이 줄어드는 추세(566 → 338ms)가 있어 워밍업 1회로는 JIT가 충분히 안정되지 않은 것으로 보임. 이후 페이징 측정은 워밍업 3회로 늘림
- **한계**: 컬렉션 fetch join에 페이징을 붙이면 Hibernate가 `HHH90003004` 경고와 함께 **전체를 메모리에 올린 뒤 잘라냄** → 페이징과 함께 쓸 수 없음

---

## 4. DTO 직접 조회

필요한 7개 컬럼만 평평한 행으로 받아 Java에서 주문별로 묶습니다.

```java
public record OrderItemRow(Long orderId, OrderStatus status, LocalDateTime orderTime,
                           Long itemId, String itemName, int orderPrice, int quantity) {}

@Query("""
    select new com.shop.backend.order.infrastructure.dto.OrderItemRow(
        o.id, o.status, o.orderTime, i.id, i.name, oi.orderPrice, oi.quantity)
    from OrderItem oi
    join oi.order o
    join oi.item i
    where o.member.id = :memberId
    order by o.id
    """)
List<OrderItemRow> findOrderRowsByMemberId(@Param("memberId") Long memberId);

// 서비스: orderId 기준 LinkedHashMap으로 그룹핑 후 응답 DTO로 변환
```
`select new`의 클래스는 JPQL 문자열 안에 있어 Java `import`가 적용되지 않으므로 전체 이름으로 씁니다.

| 회원 | 쿼리 수 | 중앙값 | 최소 / 최대 |
|---|---|---|---|
| 1000 | 1 | 173ms | 151 / 207ms |
| 711 | 1 | 143ms | 132 / 176ms |

- fetch join과 쿼리 수는 같지만 **약 210ms(55%) 빠름**. 엔티티 약 4만 개 생성과 영속성 컨텍스트 등록, 세 테이블 전체 컬럼 조회가 사라진 만큼
- **트레이드오프**: 응답 필드가 바뀔 때마다 JPQL·행 DTO·변환 코드를 함께 고쳐야 함. 행 단위 결과라 그대로 `limit`을 걸면 주문이 중간에 잘려, 페이징하려면 "주문 id 페이징 → 행 조회" 2단계가 필요

---

## 5. 페이징 + `default_batch_fetch_size: 100` (채택)

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
  data:
    web:
      pageable:
        max-page-size: 100   # size 파라미터로 전체 조회가 되살아나는 것을 방지
```
```java
Page<Order> findByMemberId(Long memberId, Pageable pageable);   // Repository

@GetMapping
public ResponseEntity<PagedModel<OrderResponse>> getOrders(
        @AuthenticationPrincipal Member member,
        @PageableDefault(size = 20, sort = "orderTime", direction = Sort.Direction.DESC) Pageable pageable)
```
`Page`를 그대로 직렬화하면 내부 구현(`PageImpl`)의 JSON 구조가 버전마다 바뀔 수 있어 `PagedModel`로 응답 형식을 고정했습니다.

### 결과 (회원 1000, 워밍업 3회 + 측정 7회)
| 페이지 | OFFSET | 반환 | 쿼리 수 | 중앙값 | 최소 / 최대 |
|---|---|---|---|---|---|
| 0 (첫 페이지) | 0 | 20 | 4 | 20ms | 12 / 25ms |
| 250 (중간) | 5,000 | 20 | 4 | 19ms | 15 / 25ms |
| 506 (마지막) | 10,120 | 8 | 3 | 12ms | 11 / 14ms |

- **쿼리 4개** = orders 1 + count 1 + order_item 1 + item 1. 가설과 정확히 일치하고, 전역 배치 설정이 적용됐음을 확인
- **마지막 페이지가 3개인 이유**: 결과(8건)가 페이지 크기보다 작으면 Spring Data가 `offset + 8`로 전체 건수를 계산할 수 있어 count 쿼리를 생략
- **가설이 틀린 점 — OFFSET이 느리지 않았음**: 마지막 페이지가 느릴 것으로 예상했지만 차이가 없었습니다. OFFSET은 앞의 행을 읽고 버리지만, `(member_id, order_time)` 인덱스에서 회원 한 명의 구간(약 1만 건)만 읽으므로 수 ms면 끝납니다. 마지막 페이지가 더 빠른 것은 반환 건수가 적고 count가 없기 때문이지 OFFSET 비용이 없어서가 아닙니다
- **판단**: 회원당 주문 1만 건 규모에서는 OFFSET 페이징으로 충분하고, 커서(No-Offset) 페이징은 필요하지 않다고 판단. 회원당 주문이 수십만 건으로 커지면 다시 측정할 대상

### 왜 DTO 조회 + 페이징이 아닌가
- 한 페이지 20건이면 엔티티는 약 80개. DTO 조회가 아낀 엔티티 생성 비용(1만 건 기준 210ms)은 20건 기준 1ms 미만
- batch size는 설정 한 줄로 **다른 조회 API의 지연 로딩에도 함께 적용**되고, 표준 `Pageable`을 그대로 쓸 수 있음
- DTO 조회는 응답 필드 변경마다 쿼리를 함께 고쳐야 하고, 페이징하려면 2단계 쿼리가 필요

---

## 한계와 후속 과제
- **측정 조건 차이**: 대안 1~4(워밍업 1·측정 5)와 페이징(워밍업 3·측정 7)의 반복 조건이 다릅니다. 대안 간 차이가 수 배라 결론에는 영향이 없지만, 100ms대 비교(fetch join vs DTO)는 JIT 워밍업 영향을 받을 수 있습니다
- **단일 요청 측정**: 동시 요청 상황(커넥션 풀 점유 시간 감소 효과)은 측정하지 않았습니다. k6 부하 테스트에서 함께 확인할 과제
- **API 응답 형식 변경**: 배열 → `{ content, page }`. 프론트엔드 주문 목록 화면 수정 필요
- **측정 중 Kafka 컨슈머**: `local` 프로필로 측정 컨텍스트를 띄우면 쿠폰 컨슈머가 함께 시작됩니다. 측정 테스트에는 `spring.kafka.listener.auto-startup=false`를 두어 측정 중 DB 쓰기와 노이즈를 막습니다
