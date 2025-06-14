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
    
    public ProxyCacheService(ServerServicePrx serverProxy, Communicator communicator) {
        this.serverProxy = serverProxy;
        
        Properties props = communicator.getProperties();
        this.cacheExpiryTime = props.getPropertyAsIntWithDefault("ProxyCache.CacheExpiryMinutes", 30) * 60 * 1000;
        this.maxCacheSize = props.getPropertyAsIntWithDefault("ProxyCache.MaxCacheSize", 50000);
        
        this.cache = new ConcurrentHashMap<>(maxCacheSize);
        this.cacheTimestamps = new ConcurrentHashMap<>(maxCacheSize);
        
        startCacheCleanup();
    }
    
    @Override
    public String findVotingStationByDocument(String document, Current current) {
        if (document == null || document.trim().isEmpty()) {
            return null;
        }
        
        String cachedResult = getCachedResult(document);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            String result = serverProxy.findVotingStationByDocument(document);
            
            CompletableFuture.runAsync(() -> {
                if (result != null) {
                    cacheResult(document, result);
                } else {
                    cacheResult(document, "NULL_RESULT");
                }
            });
            
            return result;
            
        } catch (Exception e) {
            System.err.println("ProxyCacheService: Error querying server for document " + document + ": " + e.getMessage());
            return null;
        }
    }
    
    private String getCachedResult(String document) {
        if (cache.size() > maxCacheSize * 0.9) {
            CompletableFuture.runAsync(this::cleanOldestEntries);
        }
        
        Long timestamp = cacheTimestamps.get(document);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < cacheExpiryTime) {
            String cachedValue = cache.get(document);
            if ("NULL_RESULT".equals(cachedValue)) {
                return null;
            }
            return cachedValue;
        } else if (timestamp != null) {
            CompletableFuture.runAsync(() -> {
                cache.remove(document);
                cacheTimestamps.remove(document);
            });
        }
        return null;
    }
    
    private void cacheResult(String document, String result) {
        cache.put(document, result);
        cacheTimestamps.put(document, System.currentTimeMillis());
    }
    
    private void cleanOldestEntries() {
        int toRemove = maxCacheSize / 5;
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
            
            cacheTimestamps.entrySet().removeIf(entry -> 
                (currentTime - entry.getValue()) > cacheExpiryTime
            );
            
            cache.keySet().retainAll(cacheTimestamps.keySet());
            
        }, 10, 10, TimeUnit.MINUTES);
    }
    
    public void shutdown() {
        System.out.println("ProxyCacheService shutdown completed");
    }
}