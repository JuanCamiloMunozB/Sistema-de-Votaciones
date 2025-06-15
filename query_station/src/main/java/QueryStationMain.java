import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;
import ElectionSystem.*;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class QueryStationMain {
    
    private static queryStationPrx queryStationProxy;
    private static final String[] TEST_DOCUMENTS = {
        "993157367", "809987059", "297140468", "312980321", "867053954",
        "38381016", "511828855", "484342873", "186045236", "703358020",
        "943803889", "483757285", "619121763", "648503287", "389528764",
        "605260883", "55555555", "793933660", "525467964", "646589661"
    };
    
    public static void main(String[] args) {
        QueryStationImpl queryStationImpl = null;
        
        try (Communicator communicator = Util.initialize(args, "config.query.cfg");
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("QueryStation starting...");
            
            Properties props = communicator.getProperties();
            String proxyCacheProxy = props.getProperty("ProxyCache.Proxy");
            System.out.println("ProxyCache.Proxy property: " + proxyCacheProxy);
            
            if (proxyCacheProxy == null || proxyCacheProxy.trim().isEmpty()) {
                System.err.println("ERROR: ProxyCache.Proxy property not found in configuration");
                System.err.println("Please check config.query.cfg file");
                return;
            }
            
            ObjectPrx proxyCacheBase = communicator.stringToProxy(proxyCacheProxy);
            ServerQueryServicePrx proxyCacheService = ServerQueryServicePrx.checkedCast(proxyCacheBase);
            
            if (proxyCacheService == null) {
                System.err.println("ERROR: Could not cast proxy to ServerQueryServicePrx");
                System.err.println("Check if server is running and accessible");
                return;
            }
            
            System.out.println("Successfully connected to ServerQueryService");
            
            ObjectAdapter adapter = communicator.createObjectAdapter("QueryStationAdapter");
            
            queryStationImpl = new QueryStationImpl(proxyCacheService);
            adapter.add(queryStationImpl, Util.stringToIdentity("QueryStation"));
            adapter.activate();
            
            queryStationProxy = queryStationPrx.uncheckedCast(
                adapter.createProxy(Util.stringToIdentity("QueryStation"))
            );
            
            startCLI(scanner);
            
        } catch (Exception e) {
            System.err.println("Error en QueryStation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (queryStationImpl != null) {
                queryStationImpl.shutdown();
            }
            System.out.println("QueryStation terminado");
        }
    }

    private static void handleSingleQuery(String document) {
        try {
            long startTime = System.currentTimeMillis();

            String result = queryStationProxy.query(document);
            
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            System.out.println("Response time: " + responseTime + "ms");
            if (result != null && !result.trim().isEmpty()) {
                System.out.println("Voting station found: " + result);
            } else {
                System.out.println("Citizen not found for document: " + document);
            }
            
        } catch (Exception e) {
            System.err.println("Error querying document " + document + ": " + e.getMessage());
        }
    }

    private static void startCLI(Scanner scanner) {
        boolean running = true;
        System.out.println("\nQuery Station CLI");
        System.out.println("Commands: query <document> | test [threads] [duration_seconds] [target_qps] | stats | clear | exit");
        
        while (running) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");
            String command = parts.length > 0 ? parts[0].toLowerCase() : "";
            
            try {
                switch (command) {
                    case "query":
                        if (parts.length == 2) {
                            handleSingleQuery(parts[1]);
                        } else {
                            System.err.println("Usage: query <document>");
                        }
                        break;
                        
                    case "test":
                        handleStressTest(parts);
                        break;
                        
                    case "stats":
                        handleCacheStats();
                        break;
                        
                    case "clear":
                        handleClearCache();
                        break;
                        
                    case "exit":
                        running = false;
                        break;
                        
                    default:
                        if (!command.isEmpty()) {
                            System.err.println("Unknown command: " + command);
                        }
                        System.out.println("Available commands: query <document> | test [threads] [duration_seconds] [target_qps] | stats | clear | exit");
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
            }
        }
        
        System.out.println("Shutting down Query Station CLI...");
    }
    
    private static void handleCacheStats() {
        try {
            // Need to connect to server directly for cache stats
            System.out.println("Cache stats not available through query station proxy.");
            System.out.println("This command would need direct server connection.");
        } catch (Exception e) {
            System.err.println("Error getting cache stats: " + e.getMessage());
        }
    }
    
    private static void handleClearCache() {
        try {
            // Need to connect to server directly for cache clearing
            System.out.println("Cache clear not available through query station proxy.");
            System.out.println("This command would need direct server connection.");
        } catch (Exception e) {
            System.err.println("Error clearing cache: " + e.getMessage());
        }
    }
    
    private static void handleStressTest(String[] parts) {
        int threads = 50;
        int durationSeconds = 60;
        int targetQPS = 2667;
        
        // Parse optional parameters
        if (parts.length >= 2) {
            try {
                threads = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid threads parameter, using default: " + threads);
            }
        }
        
        if (parts.length >= 3) {
            try {
                durationSeconds = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration parameter, using default: " + durationSeconds);
            }
        }
        
        if (parts.length >= 4) {
            try {
                targetQPS = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid target QPS parameter, using default: " + targetQPS);
            }
        }
        
        System.out.println("\n=== STRESS TEST CONFIGURATION ===");
        System.out.println("Threads: " + threads);
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Target QPS: " + targetQPS);
        System.out.println("Target total queries: " + (targetQPS * durationSeconds));
        System.out.println("==================================\n");
        
        runStressTest(threads, durationSeconds, targetQPS);
    }
    
    private static void runStressTest(int threads, int durationSeconds, int targetQPS) {
        System.out.println("Checking QueryStation proxy availability...");
        if (queryStationProxy == null) {
            System.err.println("ERROR: QueryStation proxy is null. Cannot run stress test.");
            return;
        }
        
        // Add a brief pause to let any initialization complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger totalQueries = new AtomicInteger(0);
        AtomicInteger successfulQueries = new AtomicInteger(0);
        AtomicInteger failedQueries = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        AtomicLong maxResponseTime = new AtomicLong(0);
        
        // Rate limiting: queries per second per thread
        int queriesPerThreadPerSecond = Math.max(1, targetQPS / threads);
        long delayBetweenQueries = Math.max(0, 1000 / queriesPerThreadPerSecond); // milliseconds
        
        System.out.println("Starting stress test...");
        System.out.println("Each thread will execute ~" + queriesPerThreadPerSecond + " queries per second");
        System.out.println("Delay between queries per thread: " + delayBetweenQueries + "ms");
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        // Submit worker threads
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                Random random = new Random();
                
                while (System.currentTimeMillis() < endTime) {
                    try {
                        String document = TEST_DOCUMENTS[random.nextInt(TEST_DOCUMENTS.length)];
                        
                        long queryStart = System.currentTimeMillis();
                        String result = queryStationProxy.query(document);
                        long queryEnd = System.currentTimeMillis();
                        
                        long responseTime = queryEnd - queryStart;
                        totalQueries.incrementAndGet();
                        successfulQueries.incrementAndGet();
                        
                        totalResponseTime.addAndGet(responseTime);
                        updateMinMax(minResponseTime, maxResponseTime, responseTime);
                        
                        if (delayBetweenQueries > 0) {
                            Thread.sleep(delayBetweenQueries);
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        failedQueries.incrementAndGet();
                        totalQueries.incrementAndGet();
                        
                        try {
                            Thread.sleep(1); // Brief pause on error
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });
            futures.add(future);
        }
        
        monitorProgress(totalQueries, successfulQueries, failedQueries, startTime, durationSeconds);
        
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // Silent error handling during stress test
            }
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        
        // Calculate final results
        long actualDuration = System.currentTimeMillis() - startTime;
        int total = totalQueries.get();
        int successful = successfulQueries.get();
        int failed = failedQueries.get();
        double actualQPS = (double) total / (actualDuration / 1000.0);
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        double avgResponseTime = successful > 0 ? (double) totalResponseTime.get() / successful : 0;
        
        System.out.println("\n=== STRESS TEST RESULTS ===");
        System.out.println("Actual duration: " + actualDuration + "ms (" + (actualDuration / 1000.0) + "s)");
        System.out.println("Total queries: " + total);
        System.out.println("Successful queries: " + successful);
        System.out.println("Failed queries: " + failed);
        System.out.println("Success rate: " + String.format("%.2f%%", successRate));
        System.out.println("Actual QPS: " + String.format("%.2f", actualQPS));
        System.out.println("Target QPS: " + targetQPS);
        System.out.println("QPS Achievement: " + String.format("%.2f%%", (actualQPS / targetQPS) * 100));
        
        if (successful > 0) {
            System.out.println("Average response time: " + String.format("%.2fms", avgResponseTime));
            System.out.println("Min response time: " + minResponseTime.get() + "ms");
            System.out.println("Max response time: " + maxResponseTime.get() + "ms");
        }
        
        // Performance verdict
        boolean targetAchieved = actualQPS >= (targetQPS * 0.80); // 80% of target is acceptable
        System.out.println("\nPERFORMANCE VERDICT: " + (targetAchieved ? "✅ PASSED" : "❌ FAILED"));
        
        if (targetAchieved) {
            System.out.println("The system successfully handled the target load!");
            if (actualQPS >= 2667) {
                System.out.println("🎉 Target of 160,000 queries per minute achieved!");
            }
        } else {
            System.out.println("The system did not meet the target performance.");
            System.out.println("Achieved " + String.format("%.0f", actualQPS) + " QPS vs target " + targetQPS + " QPS");
        }
        
        System.out.println("===========================\n");
    }
    
    private static void updateMinMax(AtomicLong minResponseTime, AtomicLong maxResponseTime, long responseTime) {
        long currentMin = minResponseTime.get();
        while (responseTime < currentMin && !minResponseTime.compareAndSet(currentMin, responseTime)) {
            currentMin = minResponseTime.get();
        }

        long currentMax = maxResponseTime.get();
        while (responseTime > currentMax && !maxResponseTime.compareAndSet(currentMax, responseTime)) {
            currentMax = maxResponseTime.get();
        }
    }
    
    private static void monitorProgress(AtomicInteger totalQueries, AtomicInteger successfulQueries, 
                                       AtomicInteger failedQueries, long startTime, int durationSeconds) {
        Thread progressMonitor = new Thread(() -> {
            try {
                int lastTotal = 0;
                for (int i = 0; i < durationSeconds; i += 5) {
                    Thread.sleep(5000);
                    
                    long elapsed = System.currentTimeMillis() - startTime;
                    int currentTotal = totalQueries.get();
                    int currentSuccessful = successfulQueries.get();
                    int currentFailed = failedQueries.get();
                    
                    double currentQPS = (double) currentTotal / (elapsed / 1000.0);
                    double recentQPS = (double) (currentTotal - lastTotal) / 5.0;
                    
                    System.out.printf("[%ds] Total: %d, Success: %d, Failed: %d, Current QPS: %.1f, Recent QPS: %.1f%n",
                            elapsed / 1000, currentTotal, currentSuccessful, currentFailed, currentQPS, recentQPS);
                    
                    lastTotal = currentTotal;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        progressMonitor.setDaemon(true);
        progressMonitor.start();
    }
}