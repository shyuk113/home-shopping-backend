# 🛒 홈쇼핑 백엔드 프로젝트

대규모 선착순 쿠폰 발급 및 주문·결제 시스템 — 동시성 제어에 집중한 백엔드 설계

**이성혁** · [danial2569@gmail.com](mailto:danial2569@gmail.com) · [github.com/shyuk113](https://github.com/shyuk113)

- **GitHub (Backend):** [github.com/shyuk113/home-shopping-backend](https://github.com/shyuk113/home-shopping-backend)
- **GitHub (Frontend):** [github.com/shyuk113/home-shopping-frontend](https://github.com/shyuk113/home-shopping-frontend)

---

## 📋 프로젝트 개요

대량의 동시 요청이 몰리는 상황에서도 서버가 안정적으로 동작하도록, 선착순 쿠폰 발급 시스템과 장바구니·주문·결제 API를 설계하고 구현한 개인 프로젝트입니다. 기능 구현 자체보다, 동시성 문제를 직접 재현하고 원인을 진단해 해결하는 과정에 집중했습니다.

### ⚡ Tech Stack

- **Backend:** Java 21, Spring Boot 4.0.2, Spring Data JPA/Hibernate, Spring Security + JWT, Gradle
- **Frontend:** React 19, TypeScript, Vite, React Router DOM
- **Data/Infrastructure:** Redis (동시성 카운터), H2 (개발용 DB)
- **Architecture:** 4-Layer Architecture (Domain / Application / Infrastructure / Presentation)
- **Domain Modules:** Auth, Member, Item, Cart, Order, Payment, Coupon
- **Test Tools:** JUnit5, ExecutorService/CountDownLatch, k6

### ✅ 핵심 기능

- JWT 기반 회원가입/로그인 인증
- 상품 조회 및 재고 관리
- 장바구니 담기 · 조회 · 삭제
- 선착순 쿠폰 발급 (수량 제한, 회원별 중복 발급 방지)
- 주문 생성 및 결제 승인 · 취소
- 결제 승인 시점 기준 재고 차감 (주문 생성 시점이 아님)

---

## 🔒 동시성 제어 및 데이터 정합성 해결

대규모 트래픽 환경에서 발생할 수 있는 동시성 이슈를 해결하기 위해, 문제를 재현하고 원인을 진단한 뒤 단계적으로 개선했습니다.

### 1) 쿠폰 대량 발급 이슈 (Lost Update 방지)

- **문제 상황:** 여러 사용자가 동시에 쿠폰을 발급받을 때, 영속성 컨텍스트의 Dirty Checking(엔티티를 읽어와 필드를 +1 한 뒤 저장)만으로 처리하면 여러 트랜잭션이 같은 값을 동시에 읽어 덮어쓰면서 Lost Update가 발생
- **해결 방안:** 애플리케이션 레벨의 Read-Modify-Write 대신, 데이터베이스에서 직접 원자적으로 값을 증가시키는 단일 JPQL 벌크 Update 쿼리로 전환

```sql
UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 WHERE c.id = :couponId
```

### 2) 재고 감소 이슈 (비관적 락 → 원자적 UPDATE)

- **문제 상황:** 동시 주문 시 재고보다 많은 주문이 성공하거나, 재고 확인과 차감 사이의 시간차(race condition)로 정합성이 깨질 위험
- **1차 시도 (비관적 락):** `PESSIMISTIC_WRITE`로 재고 조회 시 락을 걸어 동시 접근을 막는 방식을 먼저 적용했으나, 트래픽이 몰릴수록 락 대기로 인한 성능 저하가 발생
- **최종 해결 (원자적 UPDATE):** 락 없이 조건절만으로 정합성을 보장하는 조건부 UPDATE 쿼리로 전환. 영향받은 row 수가 0이면 재고 부족으로 판단

```sql
UPDATE Item i SET i.quantity = i.quantity - :qty WHERE i.id = :id AND i.quantity >= :qty
```

**추가 개선:** 재고 차감 시점을 주문 생성 시점에서 결제 승인 시점으로 옮겨, 결제되지 않는 주문 때문에 재고가 묶이는 문제를 방지

#### 📈 k6 부하테스트로 측정한 개선 효과

k6로 결제 확정(confirm) 요청 1,600건(100→500→1000명 동시 구간)을 재현해 측정한 결과, 비관적 락을 원자적 UPDATE로 전환한 뒤 p95 응답시간이 79% 감소했습니다.

| 구분 | p95 응답시간 | 평균 응답시간 | 결제 정확성 |
|---|---|---|---|
| 변경 전 (비관적 락) | 6.93s | 2.19s | 100% |
| **변경 후 (원자적 UPDATE)** | **1.48s (-79%)** | **0.58s (-73%)** | 100% |

### 3) 선착순 쿠폰 발급 원자성 (Redis)

- **문제 상황:** 쿠폰은 수량 제한이 있는 선착순 발급이라, 다수의 동시 요청에서도 정확히 설정된 수량만큼만 발급되고 회원별 중복 발급도 막아야 함
- **해결 방안:** Redis의 `SET` + `INCR` 연산으로 회원별 중복 발급 여부 확인과 발급 수량 카운트를 처리해, 초과 발급을 방지
- **한계 및 개선 계획:** `SET`과 `INCR`를 두 번의 명령으로 나눠 호출하는 구조라 완전한 원자성 보장에는 한계가 있음을 확인했고, 이후 Lua Script로 두 연산을 하나의 원자적 트랜잭션으로 묶을 계획

---

## 🧪 동시성 검증 방법

JUnit5의 `ExecutorService` + `CountDownLatch`(ready/start/done 3단계)로 다수의 스레드가 동시에 같은 API를 호출하도록 하는 테스트를 작성했습니다. 또한 k6로 실제 HTTP 부하 시나리오를 구성해, 다수의 가상 사용자가 동시 요청을 보낼 때의 응답 시간과 에러율을 확인할 수 있는 환경을 만들었습니다.

---

## 🔭 회고 및 향후 계획

- 재고 차감 시점을 결제 승인으로 옮기면서 기존 주문 동시성 테스트가 새 구조를 반영하지 못하는 것을 발견 — 결제 시점 기준으로 재작성 예정
- 실제 PG(결제대행사) 연동 없이 결제 도메인을 설계함 — 다음 단계로 실제 PG 테스트 연동 검토
- Redis `SET` + `INCR` 조합의 원자성 한계를 발견 — Lua 스크립트로 원자화할 예정
