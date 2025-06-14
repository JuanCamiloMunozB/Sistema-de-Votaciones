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
    }
    
    @Override
    public String query(String document, Current current) {
        if (document == null || document.trim().isEmpty()) {
            return null;
        }
        
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
            return result;
        } catch (Exception e) {
            System.err.println("QueryStationImpl.query: Error processing document " + document + ": " + e.getMessage());
            return null;
        }
    }
    
    public void shutdown() {
        queryExecutor.shutdown();
        try {
            if (!queryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                queryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            queryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}