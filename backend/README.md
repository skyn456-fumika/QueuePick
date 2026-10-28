# QueuePick

한정 수량 상품의 주문·재고 정합성을 중심으로 성장시키는 Java 백엔드 포트폴리오 프로젝트.

> 상태: **1주 차 초기 스캐폴드**. 현재는 도메인 초안과 DB 스키마, 단위 테스트만 포함하며 주문 API나 실제 서비스 화면은 아직 없다.

## 기술 스택
- Java 21, Spring Boot 3.5.x, Gradle
- Spring Data JPA, PostgreSQL 17, Flyway
- JUnit 5, AssertJ, Testcontainers(차후 통합 테스트에 사용)

## 로컬 실행 준비
1. JDK 21, Docker Desktop(Compose 포함), Gradle 8.14+를 설치한다.
2. `docker compose up -d`로 PostgreSQL을 시작한다.
3. `gradle bootRun`으로 서버를 시작한다.
4. `http://localhost:8080/actuator/health`의 응답을 확인한다.
5. `gradle test`로 단위 테스트를 실행한다.

현재 Gradle Wrapper는 포함되어 있지 않다. Gradle 설치 후 `gradle wrapper --gradle-version 8.14.3`을 실행하고 생성된 Wrapper 파일을 커밋할 것.

기본 DB 비밀번호는 **로컬 개발 전용**이다. 외부 배포 시 환경 변수 `POSTGRES_PASSWORD`, `DB_PASSWORD`를 동일한 안전한 값으로 지정하고 포트 노출 및 접근 제어를 재설정한다.

## 설계 기록
- [모듈러 모놀리스 선택](docs/adr/0001-modular-monolith.md)
- [도메인과 불변식](docs/architecture/domain.md)

## 개발 규칙
- 코드가 설명하는 동작을 중복 주석으로 작성하지 않는다.
- 주석에는 설계 의도, 비즈니스 제약, 트랜잭션 경계를 기록한다.
- 중요한 기술 선택은 `docs/adr/`, 실험 결과는 `docs/performance/`에 기록한다.
- 동작·성능은 테스트로 확인한 것만 성과로 명시한다.
- 변경은 기능 단위로 `feat:`, `test:`, `docs:` 등의 커밋으로 관리한다.

## 다음 단계
Product/Inventory 영속화 테스트, 주문 애플리케이션 서비스 및 트랜잭션 테스트 설계.
