import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V2: Race Condition 해결 버전
 * AtomicInteger를 사용하여 원자적 연산으로 개선
 */
public class ViewServiceV2 {
    // DB 역할을 하는 Map (AtomicInteger 사용)
    private final Map<Long, AtomicInteger> database = new ConcurrentHashMap<>();
    
    // Cache 역할을 하는 Map (AtomicInteger 사용)
    private final Map<Long, AtomicInteger> cache = new ConcurrentHashMap<>();
    
    /**
     * 조회수 증가 API (개선 버전)
     * 
     * 개선 전략:
     * - AtomicInteger를 사용하여 원자적 증가 연산 수행
     * - getAndIncrement() 메서드로 race condition 방지
     * - Cache와 DB 모두 AtomicInteger로 관리하여 일관성 유지
     */
    public int incrementViewCount(Long postId) {
        // 1. 캐시에서 AtomicInteger 가져오기
        AtomicInteger viewCount = cache.get(postId);
        
        if (viewCount == null) {
            // 2. 캐시에 없으면 DB에서 조회 후 캐시에 저장
            // computeIfAbsent를 사용하여 동시성 안전하게 처리
            viewCount = database.computeIfAbsent(postId, k -> new AtomicInteger(0));
            cache.putIfAbsent(postId, viewCount);
            
            // 동시에 여러 스레드가 접근할 경우를 대비해 다시 가져오기
            viewCount = cache.get(postId);
            System.out.println(String.format("[CACHE MISS] PostId: %d, Loaded from DB: %d", 
                postId, viewCount.get()));
        }
        
        // 3. 원자적 증가 연산 (race condition 방지)
        int newViewCount = viewCount.incrementAndGet();
        
        // 4. DB와 Cache가 같은 AtomicInteger 인스턴스를 참조하므로
        //    자동으로 동기화됨 (별도 업데이트 불필요)
        
        return newViewCount;
    }
    
    /**
     * DB의 조회수 조회
     */
    public int getViewCountFromDB(Long postId) {
        AtomicInteger value = database.get(postId);
        return value == null ? 0 : value.get();
    }
    
    /**
     * Cache의 조회수 조회
     */
    public int getViewCountFromCache(Long postId) {
        AtomicInteger value = cache.get(postId);
        return value == null ? 0 : value.get();
    }
    
    /**
     * DB와 Cache의 일관성 검사
     */
    public boolean checkConsistency(Long postId) {
        int dbValue = getViewCountFromDB(postId);
        int cacheValue = getViewCountFromCache(postId);
        
        if (dbValue != cacheValue) {
            System.out.println(String.format("[ERROR] Inconsistency detected! PostId: %d, DB: %d, Cache: %d", 
                postId, dbValue, cacheValue));
            return false;
        }
        return true;
    }
    
    /**
     * 초기화 (테스트용)
     */
    public void initialize(Long postId, int initialCount) {
        AtomicInteger initial = new AtomicInteger(initialCount);
        database.put(postId, initial);
        cache.put(postId, initial);
    }
    
    /**
     * 전체 초기화 (테스트용)
     */
    public void clear() {
        database.clear();
        cache.clear();
    }
}

