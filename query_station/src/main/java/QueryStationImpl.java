import ElectionSystem.*;
import com.zeroc.Ice.Current;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QueryStation - Cliente que usa Thread Pool Pattern para alto throughput
 * Se conecta al Proxy Cache (no directamente al servidor)
 */
public class QueryStationImpl implements queryStation {
    
    private final ServerQueryServicePrx proxyCacheService;
    
    // Thread Pool Pattern para alto throughput (2,666+ consultas/segundo)
    private final ThreadPoolExecutor queryExecutor;
    private final AtomicLong queryCounter = new AtomicLong(0);
    
    public QueryStationImpl(ServerQueryServicePrx proxyCacheService) {
        this.proxyCacheService = proxyCacheService;
        
        // Thread Pool Pattern - Configuración para alto throughput
        this.queryExecutor = new ThreadPoolExecutor(
            100, // corePoolSize - 100 threads base para 2,666+ consultas/segundo
            300, // maximumPoolSize - hasta 300 threads bajo carga extrema
            30L, TimeUnit.SECONDS, // keepAliveTime
            new LinkedBlockingQueue<>(2000), // workQueue grande para buffering
            new ThreadPoolExecutor.CallerRunsPolicy() // política de respaldo
        );
        
        // Pre-inicializar todos los threads del core para respuesta inmediata
        queryExecutor.prestartAllCoreThreads();
        
        System.out.println("QueryStationImpl initialized:");
        System.out.println("- Thread Pool: 100-300 threads (target: 2,666+ queries/second)");
        System.out.println("- Connected to Proxy Cache for high availability");
        
    }
    
    @Override
    public String query(String document, Current current) {
        long startTime = System.nanoTime();
        queryCounter.incrementAndGet();
        
        if (document == null || document.trim().isEmpty()) {
            return null;
        }
        
        System.out.println("QueryStationImpl.query: Processing document: " + document);
        
        // Thread Pool Pattern - Procesamiento concurrente masivo
        Future<String> future = queryExecutor.submit(() -> {
            try {
                // Delegar al Proxy Cache (no al servidor directamente)
                return proxyCacheService.findVotingStationByDocument(document);
            } catch (Exception e) {
                System.err.println("QueryStationImpl: Error querying proxy cache for document " + document + ": " + e.getMessage());
                return null;
            }
        });
        
        try {
            // Bloquear esperando la respuesta del proxy cache
            String result = future.get();
            
            long totalTime = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("QueryStationImpl.query: Completed for document: " + document + 
            " (time: " + totalTime + "ms)");
            
            return result;
        } catch (Exception e) {
            System.err.println("QueryStationImpl.query: Error processing document " + document + ": " + e.getMessage());
            return null;
        }
    }
    
    public void shutdown() {
        System.out.println("QueryStationImpl: Shutting down...");
        queryExecutor.shutdown();
        try {
            if (!queryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                queryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            queryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("QueryStationImpl: Shutdown completed");
    }
}