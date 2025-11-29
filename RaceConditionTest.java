import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 멀티스레드 환경에서 조회수 증가 Race Condition 테스트
 */
public class RaceConditionTest {
    
    private static final int NUM_THREADS = 100;
    private static final int CALLS_PER_THREAD = 1000;
    private static final Long POST_ID = 1L;
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(80));
        System.out.println("Race Condition 실험 시작");
        System.out.println("=".repeat(80));
        System.out.println(String.format("스레드 수: %d", NUM_THREADS));
        System.out.println(String.format("스레드당 호출 수: %d", CALLS_PER_THREAD));
        System.out.println(String.format("총 예상 조회수 증가: %d", NUM_THREADS * CALLS_PER_THREAD));
        System.out.println("=".repeat(80));
        System.out.println();
        
        // V1 테스트 (Race Condition 발생)
        testV1();
        
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println();
        
        // V2 테스트 (개선 버전)
        testV2();
    }
    
    /**
     * V1 테스트: Race Condition 발생 버전
     */
    private static void testV1() throws InterruptedException {
        System.out.println("[V1 테스트] Race Condition 발생 버전");
        System.out.println("-".repeat(80));
        
        ViewServiceV1 service = new ViewServiceV1();
        service.initialize(POST_ID, 0);
        
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        AtomicInteger totalCalls = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < CALLS_PER_THREAD; j++) {
                        service.incrementViewCount(POST_ID);
                        totalCalls.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        int expected = NUM_THREADS * CALLS_PER_THREAD;
        int dbValue = service.getViewCountFromDB(POST_ID);
        int cacheValue = service.getViewCountFromCache(POST_ID);
        boolean isConsistent = service.checkConsistency(POST_ID);
        
        System.out.println();
        System.out.println("[V1 테스트 결과]");
        System.out.println("-".repeat(80));
        System.out.println(String.format("EXPECTED = %d", expected));
        System.out.println(String.format("DB = %d", dbValue));
        System.out.println(String.format("CACHE = %d", cacheValue));
        System.out.println(String.format("실제 호출 수 = %d", totalCalls.get()));
        System.out.println(String.format("실행 시간 = %d ms", (endTime - startTime)));
        
        if (!isConsistent || dbValue != expected || cacheValue != expected) {
            System.out.println("[ERROR] inconsistent detected");
            System.out.println(String.format("손실된 조회수: %d (%.2f%%)", 
                expected - Math.max(dbValue, cacheValue),
                (double)(expected - Math.max(dbValue, cacheValue)) / expected * 100));
        } else {
            System.out.println("[OK] no inconsistency");
        }
    }
    
    /**
     * V2 테스트: 개선 버전
     */
    private static void testV2() throws InterruptedException {
        System.out.println("[V2 테스트] 개선 버전 (AtomicInteger 사용)");
        System.out.println("-".repeat(80));
        
        ViewServiceV2 service = new ViewServiceV2();
        service.initialize(POST_ID, 0);
        
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        AtomicInteger totalCalls = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < CALLS_PER_THREAD; j++) {
                        service.incrementViewCount(POST_ID);
                        totalCalls.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        
        // 결과 출력
        int expected = NUM_THREADS * CALLS_PER_THREAD;
        int dbValue = service.getViewCountFromDB(POST_ID);
        int cacheValue = service.getViewCountFromCache(POST_ID);
        boolean isConsistent = service.checkConsistency(POST_ID);
        
        System.out.println();
        System.out.println("[V2 테스트 결과]");
        System.out.println("-".repeat(80));
        System.out.println(String.format("EXPECTED = %d", expected));
        System.out.println(String.format("DB = %d", dbValue));
        System.out.println(String.format("CACHE = %d", cacheValue));
        System.out.println(String.format("실제 호출 수 = %d", totalCalls.get()));
        System.out.println(String.format("실행 시간 = %d ms", (endTime - startTime)));
        
        if (!isConsistent || dbValue != expected || cacheValue != expected) {
            System.out.println("[ERROR] inconsistent detected");
        } else {
            System.out.println("[OK] no inconsistency");
        }
    }
}

