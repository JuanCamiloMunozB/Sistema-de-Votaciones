import ElectionSystem.*;
import com.zeroc.Ice.Current;
import java.util.concurrent.*;

public class QueryStationImpl implements queryStation {
    
    private final ServerQueryServicePrx proxyCacheService;
    private final ThreadPoolExecutor queryExecutor;
    
    public QueryStationImpl(ServerQueryServicePrx proxyCacheService) {
        this.proxyCacheService = proxyCacheService;
        
        this.queryExecutor = new ThreadPoolExecutor(
            100,
            300,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Pre-inicializar todos los threads del core para respuesta inmediata
        queryExecutor.prestartAllCoreThreads();
        
        System.out.println("QueryStationImpl initialized with thread pool (core: 100, max: 300)");
    }
    
    @Override
    public String query(String document, Current current) {
        if (document == null || document.trim().isEmpty()) {
            System.out.println("QueryStationImpl: Empty document received");
            return null;
        }
        
        // Thread Pool Pattern - Procesamiento concurrente masivo
        Future<String> future = queryExecutor.submit(() -> {
            try {
                // Delegar al Proxy Cache (no al servidor directamente)
                String result = proxyCacheService.findVotingStationByDocument(document);
                if (result != null) {
                    System.out.println("QueryStationImpl: Found station for document " + document);
                } else {
                    System.out.println("QueryStationImpl: No station found for document " + document);
                }
                return result;
            } catch (Exception e) {
                System.err.println("QueryStationImpl: Error querying proxy cache for document " + document + ": " + e.getMessage());
                return null;
            }
        });
        
        try {
            // Bloquear esperando la respuesta del proxy cache
            String result = future.get(5, TimeUnit.SECONDS); // 5 second timeout
            return result;
        } catch (TimeoutException e) {
            System.err.println("QueryStationImpl.query: Timeout processing document " + document);
            future.cancel(true);
            return null;
        } catch (Exception e) {
            System.err.println("QueryStationImpl.query: Error processing document " + document + ": " + e.getMessage());
            return null;
        }
    }
    
    public void shutdown() {
        System.out.println("QueryStationImpl: Shutting down thread pool...");
        queryExecutor.shutdown();
        try {
            if (!queryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                queryExecutor.shutdownNow();
                System.out.println("QueryStationImpl: Forced shutdown completed");
            } else {
                System.out.println("QueryStationImpl: Graceful shutdown completed");
            }
        } catch (InterruptedException e) {
            queryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}