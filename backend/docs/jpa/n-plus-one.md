# JPA N+1 문제 분석 및 개선

## 1. 실험 목적

QueuePick 주문 목록 조회 과정에서 발생할 수 있는
JPA N+1 문제를 재현하고 개선 방법을 비교한다.

비교 대상:
- LAZY 기본 조회
- Fetch Join
- EntityGraph
- DTO Projection

## 2. 실험 환경

- Java 21
- Spring Boot 3.5.7
- Spring Data JPA / Hibernate 6.6
- PostgreSQL 17
- Testcontainers
- 주문 3개, 서로 다른 상품 3개

## 3. N+1 문제 재현

Order와 Product는 ManyToOne 관계이며
LAZY 로딩을 사용한다.

기본 findAll()로 주문 3개를 조회한 후
각 주문의 상품명에 접근했다.

결과:

- 주문 조회: SQL 1회
- 상품 조회: SQL 3회
- 총 SQL 실행: 4회

서로 다른 상품을 참조하도록 구성해
영속성 컨텍스트의 1차 캐시에 따른 영향을 줄였다.

## 4. 개선 방법 비교

| 방식 | SQL 실행 횟수 | 특징 |
|---|---:|---|
| LAZY | 4 | 연관 엔티티 접근 시 추가 조회 |
| Fetch Join | 1 | 주문과 상품 엔티티를 함께 조회 |
| EntityGraph | 1 | 어노테이션으로 로딩 계획 지정 |
| DTO Projection | 1 | 필요한 컬럼만 조회 |

### Fetch Join

JPQL에서 JOIN FETCH를 사용하여
Order와 Product를 함께 조회했다.

실제 SELECT 컬럼 수: 10개

### EntityGraph

@EntityGraph(attributePaths = {"product"})를 적용했다.

실험 환경에서 SQL 1회로 연관 상품까지 조회했다.

### DTO Projection

JPQL 생성자 표현식으로 필요한 데이터만 조회했다.

조회 컬럼:
- 주문 ID
- 상품명
- 주문 수량
- 주문 단가

실제 SELECT 컬럼 수: 4개

## 5. 기술 선택

QueuePick의 읽기 전용 주문 목록 API에는
DTO Projection을 우선 검토한다.

선택 이유:
1. 필요한 데이터만 조회할 수 있다.
2. 영속성 엔티티를 응답 구조와 분리할 수 있다.
3. 조회 목적이 명확하다.

반면 비즈니스 로직에서 연관 엔티티가 필요한 경우
Fetch Join 또는 EntityGraph를 검토한다.

## 6. 한계

이번 실험은 SQL 실행 횟수와 SELECT 컬럼을 비교했다.

응답 시간, 메모리 사용량, 대량 데이터 처리 성능을
측정한 실험은 아니다.

따라서 DTO Projection이 항상 더 빠르다고
결론 내릴 수는 없다.