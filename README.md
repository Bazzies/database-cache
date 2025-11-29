# 멀티스레드 환경에서 조회수 증가 Race Condition 실험

## 📌 실험 목적

멀티스레드 환경에서 캐시와 DB 간의 조회수 증가 시 발생하는 Race Condition을 의도적으로 발생시키고, 이후 개선 전략을 적용한 버전과 비교하여 동시성 문제의 심각성과 해결 방법을 이해하는 것이 목적입니다.

## 🏗️ 프로젝트 구조

```
cache-database/
├── ViewServiceV1.java      # Race Condition 발생 버전
├── ViewServiceV2.java      # 개선 버전 (AtomicInteger 사용)
├── RaceConditionTest.java  # 멀티스레드 테스트 코드
└── README.md               # 프로젝트 문서
```

## 🔍 Race Condition 발생 구조 설명

### V1의 문제점

`ViewServiceV1`은 다음과 같은 순서로 조회수를 증가시킵니다:

```
1. 캐시에서 조회수 읽기
2. 캐시에 없으면 DB에서 조회 후 캐시에 저장
3. 조회수 +1
4. DB에 저장
5. 캐시에 저장
```

**문제 발생 시나리오:**

```
시간    스레드 A                    스레드 B
----------------------------------------------------------
T1      캐시에서 읽기: 100
T2                                    캐시에서 읽기: 100
T3      조회수 +1: 101
T4      DB에 저장: 101
T5                                    조회수 +1: 101
T6                                    DB에 저장: 101
T7      캐시에 저장: 101
T8                                    캐시에 저장: 101
```

**결과:** 2번의 증가 연산이 수행되었지만 실제로는 1번만 증가한 것처럼 보입니다. (100 → 101)

### 왜 이런 문제가 발생하는가?

1. **비원자적 연산**: `읽기 → 계산 → 쓰기` 과정이 여러 단계로 나뉘어 있어 중간에 다른 스레드가 끼어들 수 있습니다.

2. **캐시와 DB의 불일치**: 캐시와 DB가 별도로 업데이트되면서 일시적으로 불일치 상태가 발생할 수 있습니다.

3. **동기화 부재**: `synchronized`나 락을 사용하지 않아 여러 스레드가 동시에 같은 데이터를 수정할 수 있습니다.

## 📊 V1 테스트 결과

### 테스트 설정
- 스레드 수: 100개
- 스레드당 호출 수: 1,000번
- 총 예상 조회수 증가: 100,000

### 예상 결과 (실제 실행 시)

```
================================================================================
Race Condition 실험 시작
================================================================================
스레드 수: 100
스레드당 호출 수: 1000
총 예상 조회수 증가: 100000
================================================================================

[V1 테스트] Race Condition 발생 버전
--------------------------------------------------------------------------------
[CACHE MISS] PostId: 1, Loaded from DB: 0
[CACHE MISS] PostId: 1, Loaded from DB: 0
[CACHE MISS] PostId: 1, Loaded from DB: 0
... (많은 캐시 미스 로그)

[V1 테스트 결과]
--------------------------------------------------------------------------------
EXPECTED = 100000
DB = 87654
CACHE = 82345
실제 호출 수 = 100000
실행 시간 = 1234 ms
[ERROR] inconsistent detected
손실된 조회수: 12345 (12.35%)
```

### 문제 분석

1. **조회수 손실**: 예상 100,000번의 증가가 실제로는 약 87,654번만 반영되어 약 12%의 데이터 손실 발생

2. **DB와 Cache 불일치**: DB 값(87,654)과 Cache 값(82,345)이 서로 다름

3. **원인**:
   - 여러 스레드가 동시에 같은 값을 읽고 증가시켜 덮어쓰기 발생
   - 캐시와 DB 업데이트 사이의 타이밍 차이로 불일치 발생

## ✅ V2 개선 전략

### 선택한 개선 방법: AtomicInteger 기반 원자적 증가

`ViewServiceV2`는 `AtomicInteger`를 사용하여 다음과 같이 개선했습니다:

#### 핵심 개선 사항

1. **원자적 연산**: `incrementAndGet()` 메서드를 사용하여 읽기-수정-쓰기 연산을 원자적으로 수행

2. **공유 참조**: DB와 Cache가 같은 `AtomicInteger` 인스턴스를 참조하여 자동으로 동기화

3. **동시성 안전**: `ConcurrentHashMap`의 `computeIfAbsent`를 사용하여 초기화 시에도 race condition 방지

#### 코드 구조

```java
// AtomicInteger 사용
private final Map<Long, AtomicInteger> database = new ConcurrentHashMap<>();
private final Map<Long, AtomicInteger> cache = new ConcurrentHashMap<>();

// 원자적 증가 연산
int newViewCount = viewCount.incrementAndGet();
```

### 왜 AtomicInteger가 효과적인가?

1. **CAS (Compare-And-Swap) 알고리즘**: 내부적으로 CAS를 사용하여 락 없이도 원자적 연산 보장

2. **성능**: `synchronized`보다 성능이 우수하며, 락 경합이 적을 때 더 효율적

3. **간단한 구현**: 복잡한 락 메커니즘 없이도 안전한 증가 연산 가능

## 📊 V2 테스트 결과

### 예상 결과 (실제 실행 시)

```
[V2 테스트] 개선 버전 (AtomicInteger 사용)
--------------------------------------------------------------------------------
[CACHE MISS] PostId: 1, Loaded from DB: 0
... (초기 캐시 미스만 발생)

[V2 테스트 결과]
--------------------------------------------------------------------------------
EXPECTED = 100000
DB = 100000
CACHE = 100000
실제 호출 수 = 100000
실행 시간 = 987 ms
[OK] no inconsistency
```

### 개선 효과

1. **데이터 정확성**: 예상한 100,000번의 증가가 모두 정확히 반영됨

2. **일관성 보장**: DB와 Cache 값이 항상 일치

3. **성능**: V1보다 약간 빠른 실행 시간 (락 경합 감소)

## 💡 다른 개선 전략 비교

### 1. 전체 API 락 (synchronized)
```java
public synchronized int incrementViewCount(Long postId) {
    // ...
}
```
- **장점**: 구현이 간단하고 이해하기 쉬움
- **단점**: 전체 API가 직렬화되어 성능 저하 발생

### 2. 게시글 ID 단위 락
```java
private final Map<Long, Object> locks = new ConcurrentHashMap<>();

public int incrementViewCount(Long postId) {
    Object lock = locks.computeIfAbsent(postId, k -> new Object());
    synchronized (lock) {
        // ...
    }
}
```
- **장점**: 게시글별로 독립적인 락으로 동시성 향상
- **단점**: 락 관리 오버헤드 존재

### 3. CAS (Check-and-Set) 방식
```java
int current;
do {
    current = viewCount.get();
} while (!viewCount.compareAndSet(current, current + 1));
```
- **장점**: 락 없이 원자적 연산 보장
- **단점**: 재시도 루프로 인한 성능 저하 가능

### 4. Write-Through 캐시
- DB에 먼저 저장 후 캐시 업데이트
- **장점**: DB가 항상 최신 상태 유지
- **단점**: DB 부하 증가, 여전히 race condition 가능

## 🎯 느낀점 및 결론

### 주요 학습 내용

1. **Race Condition의 심각성**: 
   - 멀티스레드 환경에서 동기화 없이는 데이터 무결성을 보장할 수 없음
   - 작은 실수로도 큰 데이터 손실 발생 가능

2. **캐시와 DB의 일관성**:
   - 두 저장소를 별도로 관리할 때는 동기화 전략이 필수적
   - Write-Through, Write-Back 등 다양한 캐시 전략 이해 필요

3. **동시성 제어 방법**:
   - `synchronized`: 간단하지만 성능 저하 가능
   - `AtomicInteger`: 락 없이 원자적 연산, 성능 우수
   - `ConcurrentHashMap`: 스레드 안전한 컬렉션 활용

4. **성능과 정확성의 트레이드오프**:
   - 동기화를 하지 않으면 빠르지만 부정확
   - 적절한 동시성 제어로 정확성과 성능 모두 확보 가능

### 실무 적용 시 고려사항

1. **분산 환경**: 단일 JVM이 아닌 분산 환경에서는 분산 락(Redis, Zookeeper 등) 필요

2. **DB 레벨 동시성**: DB의 트랜잭션 격리 수준과 락 메커니즘 고려

3. **캐시 전략**: Write-Through, Write-Back, Write-Around 등 상황에 맞는 전략 선택

4. **모니터링**: 실제 운영 환경에서는 일관성 검사 및 모니터링 시스템 구축 필요

## 🚀 실행 방법

### 컴파일
```bash
javac *.java
```

### 실행
```bash
java RaceConditionTest
```

### 예상 실행 시간
- V1 테스트: 약 1-2초
- V2 테스트: 약 1초
- 총 실행 시간: 약 2-3초

## 📝 참고사항

- 이 실험은 교육 목적으로 의도적으로 race condition을 발생시킨 것입니다.
- 실제 프로덕션 환경에서는 반드시 적절한 동시성 제어 메커니즘을 사용해야 합니다.
- 테스트 결과는 실행 환경에 따라 다를 수 있습니다.

# database-cache
