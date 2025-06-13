import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;

import ElectionSystem.*;
import java.util.concurrent.*;

public class ProxyCacheService implements ServerQueryService {
    
    private final ServerServicePrx serverProxy;
    
    private final long cacheExpiryTime;
    private final int maxCacheSize;

    private final ConcurrentHashMap<String, String> cache;
    private final ConcurrentHashMap<String, Long> cacheTimestamps;
    private final ThreadPoolExecutor cacheExecutor;
    
    public ProxyCacheService(ServerServicePrx serverProxy, Communicator communicator) {
        this.serverProxy = serverProxy;
        
        Properties props = communicator.getProperties();
        this.cacheExpiryTime = props.getPropertyAsIntWithDefault("ProxyCache.CacheExpiryMinutes", 15) * 60 * 1000;
        this.maxCacheSize = props.getPropertyAsIntWithDefault("ProxyCache.MaxCacheSize", 10000);
        
        this.cache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        
        this.cacheExecutor = new ThreadPoolExecutor(
            10, 20, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        startCacheCleanup();
    }
    
    @Override
    public String findVotingStationByDocument(String document, Current current) {
        if (document == null || document.trim().isEmpty()) {
            return null;
        }
        
        // Cache check
        String cachedResult = getCachedResult(document);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // Query server
        try {
            String result = serverProxy.findVotingStationByDocument(document);
            
            if (result != null) {
                cacheResultAsync(document, result);
            } else {
                cacheResultAsync(document, "NULL_RESULT");
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("ProxyCacheService: Error querying server for document " + document + ": " + e.getMessage());
            return null;
        }
    }
    
    private String getCachedResult(String document) {
        // Check cache size limit
        if (cache.size() > maxCacheSize) {
            cacheExecutor.submit(this::cleanOldestEntries);
        }
        
        // Check if the document is in cache and not expired
        Long timestamp = cacheTimestamps.get(document);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < cacheExpiryTime) {
            String cachedValue = cache.get(document);
            if ("NULL_RESULT".equals(cachedValue)) {
                return null;
            }
            return cachedValue;
        } else if (timestamp != null) { // If expired, remove from cache
            cacheExecutor.submit(() -> {
                cache.remove(document);
                cacheTimestamps.remove(document);
            });
        }
        return null;
    }
    
    private void cacheResultAsync(String document, String result) {
        cacheExecutor.submit(() -> {
            cache.put(document, result);
            cacheTimestamps.put(document, System.currentTimeMillis());
        });
    }
    
    private void cleanOldestEntries() {
        // Remove oldest 10% of entries when cache is full
        int toRemove = maxCacheSize / 10;
        cacheTimestamps.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByValue())
            .limit(toRemove)
            .forEach(entry -> {
                cache.remove(entry.getKey());
                cacheTimestamps.remove(entry.getKey());
            });
    }
    
    private void startCacheCleanup() {
        ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            
            for (String key : cacheTimestamps.keySet()) {
                Long timestamp = cacheTimestamps.get(key);
                if (timestamp != null && (currentTime - timestamp) > cacheExpiryTime) {
                    cache.remove(key);
                    cacheTimestamps.remove(key);
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    public void shutdown() {
        cacheExecutor.shutdown();
        try {
            if (!cacheExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cacheExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}