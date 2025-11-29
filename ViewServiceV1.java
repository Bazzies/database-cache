import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1: Race Condition이 발생하는 조회수 증가 서비스
 * 동기화 없이 구현하여 의도적으로 race condition을 발생시킴
 */
public class ViewServiceV1 {
    // DB 역할을 하는 Map (동기화 없음)
    private final Map<Long, Integer> database = new ConcurrentHashMap<>();
    
    // Cache 역할을 하는 Map
    private final Map<Long, Integer> cache = new ConcurrentHashMap<>();
    
    /**
     * 조회수 증가 API (Race Condition 발생 버전)
     * 
     * 로직:
     * 1. 캐시에서 조회수 읽기
     * 2. 캐시에 없으면 DB에서 조회 후 캐시에 저장
     * 3. 조회수 +1
     * 4. DB에 저장
     * 5. 캐시에 저장
     * 6. 최종 조회수 반환
     * 
     * 문제점: 각 단계 사이에 다른 스레드가 끼어들 수 있어 race condition 발생
     */
    public int incrementViewCount(Long postId) {
        // 1. 캐시에서 조회수 읽기
        Integer viewCount = cache.get(postId);
        
        if (viewCount == null) {
            // 2. 캐시에 없으면 DB에서 조회 후 캐시에 저장
            viewCount = database.getOrDefault(postId, 0);
            cache.put(postId, viewCount);
            System.out.println(String.format("[CACHE MISS] PostId: %d, Loaded from DB: %d", postId, viewCount));
        }
        
        // 3. 조회수 +1 (여기서 다른 스레드가 끼어들 수 있음)
        int newViewCount = viewCount + 1;
        
        // 4. DB에 저장 (다른 스레드가 이미 업데이트했을 수 있음)
        database.put(postId, newViewCount);
        
        // 5. 캐시에 저장 (다른 스레드가 이미 업데이트했을 수 있음)
        cache.put(postId, newViewCount);
        
        return newViewCount;
    }
    
    /**
     * DB의 조회수 조회
     */
    public int getViewCountFromDB(Long postId) {
        return database.getOrDefault(postId, 0);
    }
    
    /**
     * Cache의 조회수 조회
     */
    public int getViewCountFromCache(Long postId) {
        return cache.getOrDefault(postId, 0);
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
        database.put(postId, initialCount);
        cache.put(postId, initialCount);
    }
    
    /**
     * 전체 초기화 (테스트용)
     */
    public void clear() {
        database.clear();
        cache.clear();
    }
}

