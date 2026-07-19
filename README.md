# 클린알바맵 Backend

전남대 클린알바맵 백엔드 서버. 사업장 리뷰·클린지수 관리, 관리자 리뷰 승인, 인증자료 저장을 담당하는 REST API를 제공합니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Framework | Spring Boot 4.0.6 |
| Build Tool | Gradle |
| Language | Java 17 |
| ORM | Spring Data JPA (Hibernate) |
| Database | MySQL 8.4 (AWS RDS) / H2 (로컬·테스트) |
| DB Migration | Flyway |
| API | Spring Web MVC 기반 REST API |
| Auth | Kakao OAuth, JWT (jjwt) + 커스텀 인증 필터 |
| File Storage | AWS S3 (AWS SDK v2, EC2 IAM 역할 인증) |
| External API | Kakao Local API (장소 검색), Upstage Solar LLM (후기 순화) |
| Validation | Bean Validation |
| Test | JUnit 5, Spring Boot Test 통합 테스트 |
| Deploy | AWS EC2 (RDS·S3 연동) |

Spring Boot와 Gradle을 기반으로 REST API 서버를 구성하고, Spring Data JPA로 MySQL(AWS RDS) 데이터를 관리하며 Flyway로 스키마 마이그레이션을 자동화했습니다. Kakao OAuth와 JWT 기반 커스텀 인증 필터로 로그인·관리자 권한 처리를 적용했고, 리뷰 인증자료는 AWS S3에 저장하도록 구현했습니다. Kakao Local API로 사업장 위치를 검색·등록하고, Upstage Solar LLM을 연동해 후기 순화 기능을 제공하며, JUnit 5 통합 테스트로 주요 API 흐름을 검증합니다. 배포는 AWS EC2 환경에서 RDS·S3와 연동해 운영합니다.
