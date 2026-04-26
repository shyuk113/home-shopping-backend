# 🛒 Home Shopping Backend

Spring Boot 기반의 홈쇼핑 백엔드 프로젝트입니다.  
JWT 인증, Redis 캐싱, 선착순 쿠폰 발급 등 실무에서 자주 쓰이는 기능들을 구현했습니다.

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Security | Spring Security, JWT (jjwt 0.11.5) |
| Database | H2 (개발), JPA / Hibernate |
| Cache | Redis |
| Build | Gradle |
| Etc | Lombok |

---

## ✨ 주요 기능

- **회원** : 회원가입 / 로그인 (JWT 인증), 회원 정보 조회 및 수정
- **상품** : 상품 목록 및 상세 조회 / 등록 / 수정 / 삭제 (ADMIN), Redis 캐싱
- **장바구니** : 상품 추가 / 목록 조회 / 삭제
- **주문** : 주문 생성 / 목록 조회 / 단건 조회 / 취소 (재고 자동 복구)
- **쿠폰** : 선착순 쿠폰 발급 (Redis INCR 기반 동시성 처리), 내 쿠폰 목록 조회

---

## 📁 패키지 구조

```
com.shop.backend
├── Auth
│   ├── controller
│   ├── dto
│   └── service
├── Cart
│   ├── application/service
│   ├── domain
│   └── presentation
├── Coupon
│   ├── application/service
│   ├── domain
│   └── presentation
├── Item
│   ├── application/service
│   ├── domain
│   └── presentation
├── Member
│   ├── application/service
│   ├── domain
│   └── presentation
├── Order
│   ├── application/service
│   ├── domain
│   └── presentation
└── global
    ├── config
    ├── exception
    └── jwt
```

---

## 📌 API 명세

### Auth
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| POST | /api/auth/signup | 회원가입 | 누구나 |
| POST | /api/auth/login | 로그인 | 누구나 |

### Member
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| GET | /api/members/{id} | 회원 정보 조회 | 회원 |
| PUT | /api/members/{id} | 회원 정보 수정 | 회원 |

### Item
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| GET | /api/items | 상품 목록 조회 | 누구나 |
| GET | /api/items/{id} | 상품 상세 조회 | 누구나 |
| POST | /api/items | 상품 등록 | ADMIN |
| PUT | /api/items/{id} | 상품 수정 | ADMIN |
| DELETE | /api/items/{id} | 상품 삭제 | ADMIN |

### Cart
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| POST | /api/cart | 장바구니 상품 추가 | 회원 |
| GET | /api/cart | 장바구니 목록 조회 | 회원 |
| DELETE | /api/cart/{itemId} | 장바구니 상품 삭제 | 회원 |

### Order
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| POST | /api/items/orders | 주문 생성 | 회원 |
| GET | /api/items/orders | 주문 목록 조회 | 회원 |
| GET | /api/items/orders/{id} | 주문 단건 조회 | 회원 |
| DELETE | /api/items/orders/{id} | 주문 취소 | 회원 |

### Coupon
| Method | URL | 설명 | 권한 |
|--------|-----|------|------|
| POST | /api/coupons | 쿠폰 생성 | ADMIN |
| POST | /api/coupons/{couponId}/issue | 선착순 쿠폰 발급 | 회원 |
| GET | /api/coupons/my | 내 쿠폰 목록 조회 | 회원 |

---

## 🗄 ERD

```
Member ──── Cart ──── CartItem ──── Item
  │                                  │
  └──── Order ──── OrderItem ────────┘
  │
  └──── MemberCoupon ──── Coupon
```

---

## ⚙️ 환경 설정

`src/main/resources/application.yml`

```yaml
jwt:
  secret: your-secret-key

spring:
  datasource:
    url: jdbc:h2:mem:shop
    driver-class-name: org.h2.Driver
    username: sa
    password:
  redis:
    host: localhost
    port: 6379
```

---

## 🚀 실행 방법

```bash
# 1. Redis 실행
docker run -d -p 6379:6379 redis

# 2. 프로젝트 실행
./gradlew bootRun
```

---

## 🔥 트러블슈팅 / 기술적 의사결정

### 1. 재고 차감 동시성 문제 - 비관적 락 선택
여러 사용자가 동시에 주문할 때 재고가 마이너스가 되는 레이스 컨디션이 발생할 수 있습니다.

**낙관적 락 vs 비관적 락**
- 낙관적 락 : 충돌 시 재시도 로직이 필요하고, 재고 차감처럼 충돌이 잦은 상황에선 재시도가 많이 발생해 오히려 성능이 저하될 수 있습니다.
- 비관적 락 : DB 레벨에서 `SELECT FOR UPDATE`로 행에 락을 걸어 한 번에 하나의 트랜잭션만 처리합니다. 재고 차감처럼 충돌이 잦고 정확성이 중요한 상황에 적합합니다.

→ `ItemRepository`에 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 적용하고 3초 타임아웃을 설정했습니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("SELECT i FROM Item i WHERE i.id = :id")
Optional<Item> findByIdWithLock(@Param("id") Long id);
```

---

### 2. 선착순 쿠폰 발급 동시성 문제 - Redis INCR 선택
선착순 쿠폰은 짧은 시간에 트래픽이 폭발적으로 몰리는 특성이 있어 DB 락으로는 병목이 심하게 발생합니다.

**Redis INCR을 선택한 이유**
- Redis의 `INCR` 명령어는 단일 스레드로 동작해 원자성이 보장됩니다.
- DB 락과 달리 Redis는 인메모리라 응답 속도가 매우 빠릅니다.
- `SADD`로 중복 발급 체크와 등록을 원자적으로 처리할 수 있습니다.

```
1. SADD coupon:{id}:members {memberId} → 0이면 중복 발급
2. INCR coupon:{id}:count → totalQuantity 초과 시 Redis 롤백
3. DB에 MemberCoupon 저장
```

---

### 3. @Transactional + Redis 혼용 문제
`@Transactional` 메서드 안에서 Redis 작업과 DB 작업을 함께 처리하면 DB 트랜잭션 롤백 시 Redis는 롤백되지 않아 수량 불일치가 발생합니다.

**해결 방법**
- `issueCoupon()`에서 `@Transactional`을 제거해 Redis 작업과 DB 작업을 분리했습니다.
- DB 저장 로직을 `CouponIssueService`로 별도 분리해 `@Transactional`이 정상 동작하도록 했습니다.
- DB 저장 실패 시 `catch` 블록에서 Redis를 수동으로 롤백합니다.

> 같은 클래스 내에서 `@Transactional` 메서드를 호출하면 Spring 프록시를 거치지 않아 트랜잭션이 적용되지 않는 문제도 클래스 분리로 함께 해결했습니다.

---

### 4. Redis 캐싱으로 상품 조회 성능 개선
상품 목록/상세 조회는 읽기 요청이 많고 자주 바뀌지 않아 캐싱 효과가 큽니다.

- `@Cacheable`로 조회 시 캐시 우선 반환
- 상품 수정/삭제/재고 변경 시 `@CacheEvict`로 캐시 무효화
- TTL 10분 설정으로 오래된 캐시 자동 제거
