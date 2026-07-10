# 🛒 Home Shopping Backend

대량 동시 요청(선착순 쿠폰 발급, 한정 수량 주문)에도 안정적으로 동시성을 제어하는 것을 목표로 하는 Spring Boot 기반 이커머스 백엔드입니다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Security | Spring Security, JWT (jjwt 0.11.5, HS256) |
| Database | H2 (개발/테스트, in-memory, `ddl-auto: create`) — PostgreSQL 드라이버는 포함돼 있으나 아직 설정 연결은 안 됨 |
| ORM | Spring Data JPA / Hibernate |
| Cache / 동시성 제어 | Redis (Spring Cache 추상화 + 쿠폰 발급 카운터) |
| Build | Gradle |
| Etc | Lombok |

---

## 주요 기능

- **회원**: 이메일/비밀번호 회원가입(BCrypt 암호화, 이메일 중복 체크) / 로그인(JWT 발급) / 회원정보 조회·수정
- **상품**: 목록/상세 조회(Redis 캐싱, `@Cacheable`), 등록·수정·삭제(ADMIN), 수정/삭제 시 캐시 무효화
- **장바구니**: 상품 추가(이미 담긴 상품이면 수량 합산) / 조회 / 삭제
- **주문**: 주문 생성(`PENDING` 상태로 생성, 재고는 아직 안 건드림) / 목록·단건 조회 / 취소
- **결제**: 결제 요청 생성 → 승인 확정(이 시점에 실제 재고 차감) → 조회 / 취소
- **쿠폰**: 선착순 쿠폰 발급(Redis SET + INCR 기반 동시성 제어, 실패 시 보상 처리) / 내 쿠폰 목록 조회

---

## 패키지 구조

각 도메인이 `domain / application / infrastructure / presentation` 4계층 구조를 동일하게 반복합니다.

- `com.shop.backend.auth` — 회원가입/로그인
- `com.shop.backend.member` — 회원 정보
- `com.shop.backend.Item` — 상품
- `com.shop.backend.cart` — 장바구니
- `com.shop.backend.order` — 주문 (결제 대기/완료/취소/실패 상태 관리)
- `com.shop.backend.payment` — 결제 (Payment 엔티티, 결제 승인 흐름)
- `com.shop.backend.coupon` — 선착순 쿠폰
- `com.shop.backend.config` — Redis 등 공통 설정
- `com.shop.backend.global` — JWT, 예외 처리, 시큐리티 설정

---

## 핵심 설계 — 동시성 제어

| 자원 | 방식 | 이유 |
|---|---|---|
| 재고(Item, 결제 확정 시) | 조건부 원자적 UPDATE — `SET quantity = quantity - :qty WHERE id = :id AND quantity >= :qty` | 락을 잡고 대기시키는 대신, 재고 체크와 차감을 DB가 하나의 원자 연산으로 처리. 영향받은 row 수(0/1)로 성공 여부 판단 |
| 쿠폰 발급 수량 | Redis `SET`(회원별 중복 발급 체크) + `INCR`(전체 발급 수량 체크) | 스파이크성 트래픽을 DB 커넥션 풀 대신 Redis에서 흡수. 실패 시 SET 제거 + 카운트 감소로 보상 처리 |
| 쿠폰 발급 통계(`issuedQuantity`) | DB 원자적 `UPDATE` 쿼리 (`SET issuedQuantity = issuedQuantity + 1`) | 엔티티 dirty-checking 방식은 동시 갱신 시 Lost Update 발생 위험 — 원자 연산으로 전환해 해결 |

**재고 차감 시점**: 주문 생성이 아니라 **결제 확정(`PaymentService.confirm()`) 시점**에 발생합니다. `OrderService.order()`는 주문을 `PENDING` 상태로 만들 뿐 재고를 잠그거나 차감하지 않고, `item.getQuantity() < quantity` 형태의 소프트 체크만 합니다.

---

## API 명세

### Auth (`/api/auth`) — 인증 불필요
| Method | URL | 설명 |
|---|---|---|
| POST | /api/auth/signup | 회원가입 |
| POST | /api/auth/login | 로그인 (JWT 발급) |

### Member (`/api/members`) — 인증 필요
| Method | URL | 설명 |
|---|---|---|
| GET | /api/members/{id} | 회원 정보 조회 |
| PUT | /api/members/{id} | 회원 정보 수정 |

### Item (`/api/items`)
| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | /api/items | 전체 상품 조회 | 누구나 |
| GET | /api/items/{id} | 상품 상세 조회 (재고 수량 미포함) | 누구나 |
| POST | /api/items | 상품 등록 | ADMIN |
| PUT | /api/items/{id} | 상품 수정 | ADMIN |
| DELETE | /api/items/{id} | 상품 삭제 | ADMIN |

### Cart (`/api/cart`) — 인증 필요
| Method | URL | 설명 |
|---|---|---|
| POST | /api/cart | 장바구니 상품 추가 |
| GET | /api/cart | 장바구니 조회 |
| DELETE | /api/cart/{itemId} | 장바구니 상품 삭제 |

### Order (`/api/orders`)
| Method | URL | 설명 |
|---|---|---|
| POST | /api/orders | 주문 생성 (PENDING 상태) |
| GET | /api/orders?memberId={id} | 회원 주문 목록 조회 |
| GET | /api/orders/{id} | 주문 단건 조회 |
| DELETE | /api/orders/{id} | 주문 취소 (결제완료 건만 재고 복구) |

### Payment (`/api/payments`)
| Method | URL | 설명 |
|---|---|---|
| POST | /api/payments | 결제 요청 생성 |
| POST | /api/payments/{paymentKey}/confirm | 결제 승인 확정 (실제 재고 차감 발생) |
| GET | /api/payments/{paymentKey} | 결제 상태 조회 |
| POST | /api/payments/{paymentKey}/cancel | 결제 취소 |

### Coupon (`/api/coupons`)
| Method | URL | 설명 |
|---|---|---|
| POST | /api/coupons | 쿠폰 생성 |
| POST | /api/coupons/{couponId}/issue | 선착순 쿠폰 발급 |
| GET | /api/coupons/my | 내 쿠폰 목록 조회 |

---

## ERD

```mermaid
erDiagram
    MEMBER ||--o| CART : "보유"
    CART ||--o{ CART_ITEM : "담음"
    CART_ITEM }o--|| ITEM : "참조"
    MEMBER ||--o{ ORDERS : "주문"
    ORDERS ||--o{ ORDER_ITEM : "포함"
    ORDER_ITEM }o--|| ITEM : "참조"
    ORDERS ||--o{ PAYMENT : "결제 시도"
    MEMBER ||--o{ MEMBER_COUPON : "발급받음"
    COUPON ||--o{ MEMBER_COUPON : "발급됨"
```

---

## 실행 방법

```bash
# Redis 실행 (쿠폰/캐시 기능에 필요)
docker run -p 6379:6379 redis

# 애플리케이션 실행
./gradlew bootRun
```

---

## 테스트

```bash
./gradlew test --tests OrderConcurrencyTest
./gradlew test --tests CouponServiceTest
```

`CouponServiceTest`는 로컬 Redis가 떠 있어야 통과합니다.

---

## 알려진 이슈 / TODO

- [ ] `OrderConcurrencyTest`가 결제 확정 시점으로 재고 차감이 옮겨진 이후 더 이상 실제 동작을 검증하지 못함 — `PaymentService.confirm()` 기준 `PaymentConcurrencyTest`로 재작성 필요 (최우선)
- [ ] `OrderService.order()`의 재고 체크가 `<=`로 되어 있어, 재고와 요청 수량이 정확히 같을 때 잘못 거부되는 off-by-one 버그
- [ ] `PaymentController`의 `/{paymentKey}/comfirm` 경로 오타 (`confirm`으로 수정 필요)
- [ ] `POST /api/coupons`(쿠폰 생성)에 ADMIN 권한 체크 없음
- [ ] `SecurityConfig`에 남아있는 `/api/items/orders/**` 규칙은 실제 매핑(`/api/orders`)과 어긋난 죽은 규칙, `/api/payments/**`에는 명시적 규칙 자체가 없음
- [ ] `GET /api/items/{id}` 응답에 재고 수량이 빠져 있음 (`ItemDetailDto`에 quantity 필드 없음) — 재고 포함된 `ItemResponseDto`/`getItemDetail()`은 정의만 되고 미사용
- [ ] `ItemService.reduceStock()` 미사용 — 재고 차감 로직이 `ItemRepository.decreaseStock()`으로 이전되며 남은 죽은 코드
- [ ] 실제 PG(결제대행사) 연동 없음 — `confirm()`은 클라이언트가 보낸 금액만 검증, PG 서버-to-서버 승인 검증 없음
- [ ] 재고 부족으로 결제 실패 시 PG 환불 처리 미구현
- [ ] Redis `SET add` + `INCR`이 별도 명령이라 완전한 원자성은 없음 (Lua 스크립트로 개선 가능, 낮은 우선순위)
