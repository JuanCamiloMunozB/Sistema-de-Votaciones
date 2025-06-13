import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;

import ElectionSystem.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyCacheService implements ServerQueryService {
    
    private final ServerServicePrx serverProxy;
    private final Communicator communicator;
    
    // Configuración desde archivos
    private final long cacheExpiryTime;
    private final int maxCacheSize;
    
    // Cache distribuido
    private final ConcurrentHashMap<String, String> cache;
    private final ConcurrentHashMap<String, Long> cacheTimestamps;
    private final ThreadPoolExecutor cacheExecutor;
    
    // Métricas
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong serverRequests = new AtomicLong(0);
    
    public ProxyCacheService(ServerServicePrx serverProxy, Communicator communicator) {
        this.serverProxy = serverProxy;
        this.communicator = communicator;
        
        // Leer configuración
        Properties props = communicator.getProperties();
        this.cacheExpiryTime = props.getPropertyAsIntWithDefault("ProxyCache.CacheExpiryMinutes", 15) * 60 * 1000;
        this.maxCacheSize = props.getPropertyAsIntWithDefault("ProxyCache.MaxCacheSize", 10000);
        
        this.cache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        
        // Thread Pool configurado
        this.cacheExecutor = new ThreadPoolExecutor(
            10, 20, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        System.out.println("ProxyCacheService initialized:");
        System.out.println("- Cache expiry: " + (cacheExpiryTime / 60000) + " minutes");
        System.out.println("- Max cache size: " + maxCacheSize);
        System.out.println("- Connected to multiple servers via IceGrid");
        
        startCacheCleanup();
    }
    
    @Override
    public String findVotingStationByDocument(String document, Current current) {
        long startTime = System.nanoTime();
        totalRequests.incrementAndGet();
        
        if (document == null || document.trim().isEmpty()) {
            return null;
        }
        
        System.out.println("ProxyCacheService.findVotingStationByDocument: Processing document: " + document);
        
        // Cache check
        String cachedResult = getCachedResult(document);
        if (cachedResult != null) {
            cacheHits.incrementAndGet();
            long totalTime = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("ProxyCacheService: Cache HIT for document: " + document + 
                " (time: " + totalTime + "ms)");
            return cachedResult;
        }
        
        // Query server (IceGrid handles load balancing)
        try {
            System.out.println("ProxyCacheService: Cache MISS for document: " + document + " - Querying server via IceGrid...");
            serverRequests.incrementAndGet();
            
            String result = serverProxy.findVotingStationByDocument(document);
            
            // Cache result asynchronously
            if (result != null) {
                cacheResultAsync(document, result);
            } else {
                cacheResultAsync(document, "NULL_RESULT");
            }
            
            long totalTime = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("ProxyCacheService: Server response for document: " + document + 
                " (time: " + totalTime + "ms)");
            
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
        
        Long timestamp = cacheTimestamps.get(document);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < cacheExpiryTime) {
            String cachedValue = cache.get(document);
            if ("NULL_RESULT".equals(cachedValue)) {
                return null;
            }
            return cachedValue;
        } else if (timestamp != null) {
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
            int removedEntries = 0;
            
            for (String key : cacheTimestamps.keySet()) {
                Long timestamp = cacheTimestamps.get(key);
                if (timestamp != null && (currentTime - timestamp) > cacheExpiryTime) {
                    cache.remove(key);
                    cacheTimestamps.remove(key);
                    removedEntries++;
                }
            }
            
            if (removedEntries > 0) {
                System.out.println("ProxyCacheService: Cleaned " + removedEntries + " expired cache entries");
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    public void shutdown() {
        System.out.println("ProxyCacheService: Shutting down...");
        cacheExecutor.shutdown();
        try {
            if (!cacheExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cacheExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("ProxyCacheService: Shutdown completed");
    }
}