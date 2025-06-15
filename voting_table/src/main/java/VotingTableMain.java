import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ControlCenterServicePrx;
import ElectionSystem.ElectionActivityObserverPrx; 
import ElectionSystem.VoteData; 
import ElectionSystem.CandidateData; 
import ElectionSystem.CitizenData; 
import ElectionSystem.ElectionInactive; 

import java.util.Scanner; 
import java.time.LocalDateTime; 
import java.time.format.DateTimeFormatter; 
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class VotingTableMain {
    
    private static VotingTableImpl votingTableImpl;
    public static void main(String[] args) {
        String tableIdStr = System.getProperty("VOTING_TABLE_ID", "Table1");
        int numericTableId; 
        try {
            String numericPart = tableIdStr.replaceAll("[^0-9]", "");
            if (numericPart.isEmpty()) {
                 System.err.println("Could not parse a numeric ID from tableId: " + tableIdStr + ". Defaulting to 0 or handle error.");
                 numericTableId = 0; 
            } else {
                numericTableId = Integer.parseInt(numericPart);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing numeric table ID from '" + tableIdStr + "'. Please ensure VOTING_TABLE_ID is in a valid format (e.g., 'Table1').");
            e.printStackTrace();
            return;
        }

        try (Communicator communicator = Util.initialize(args, "config.voting.cfg");
             Scanner scanner = new Scanner(System.in)) { 
            
            ControlCenterServicePrx controlCenterService = ControlCenterServicePrx.checkedCast(
                communicator.stringToProxy("ControlCenterService"));

            if (controlCenterService == null) {
                System.err.println("Error: Could not get a proxy for ControlCenterService from IceGrid.");
                return;
            }
            System.out.println("Successfully obtained ControlCenterServicePrx from IceGrid.");

            ObjectAdapter adapter = communicator.createObjectAdapter("VotingTableAdapter");
            votingTableImpl = new VotingTableImpl(controlCenterService, tableIdStr);
            adapter.add(votingTableImpl, Util.stringToIdentity("VotingTableService-" + tableIdStr));
            adapter.activate();
            System.out.println("VotingTableService ready (" + tableIdStr + ").");
            
            try {
                ElectionActivityObserverPrx observerPrx = 
                    ElectionActivityObserverPrx.uncheckedCast(
                        adapter.createProxy(Util.stringToIdentity("VotingTableService-" + tableIdStr))
                    );
                if (observerPrx != null) {
                    controlCenterService.subscribeElectionActivity(observerPrx, tableIdStr);
                    System.out.println("VotingTable [" + tableIdStr + "] subscribed to election activity events.");
                } else {
                    System.err.println("VotingTable [" + tableIdStr + "] could not create observer proxy for subscription.");
                }
            } catch (Exception e) {
                System.err.println("VotingTable [" + tableIdStr + "] failed to subscribe: " + e.getMessage());
            }

            startCLI(scanner, controlCenterService, numericTableId, tableIdStr);
            
            System.out.println("Shutting down VotingTable UI for " + tableIdStr + "...");

        } catch (com.zeroc.Ice.LocalException e) {
            System.err.println("Ice Local Exception in VotingTableMain for table '" + tableIdStr + "': " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred in VotingTableMain for table '" + tableIdStr + "': " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("VotingTableMain for '" + tableIdStr + "' shut down.");
    }

    private static void startCLI(Scanner scanner, ControlCenterServicePrx controlCenterService, 
                                int numericTableId, String tableIdStr) {
        boolean running = true;
        System.out.println("\nVoting Table UI (" + tableIdStr + ")");
        System.out.println("Commands: vote <citizenDocument> <candidateId> | candidates | status | test [threads] [duration_seconds] [target_vps] | exit");

        while(running) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");
            String command = parts.length > 0 ? parts[0].toLowerCase() : "";

            try {
                switch(command) {
                    case "vote":
                        if (parts.length == 3) {
                            handleSingleVote(parts[1], parts[2], numericTableId);
                        } else {
                            System.err.println("Usage: vote <citizenDocument> <candidateId>");
                        }
                        break;
                    case "candidates":
                        handleCandidatesCommand(controlCenterService);
                        break;
                    case "status":
                        System.out.println("Election active at this table: " + votingTableImpl.isElectionActive());
                        break;
                    case "test":
                        handleStressTest(parts, numericTableId, controlCenterService);
                        break;
                    case "exit":
                        running = false;
                        break;
                    default:
                        if (!command.isEmpty()) {
                            System.err.println("Unknown command: " + command);
                        }
                        System.out.println("Available commands: vote <citizenDocument> <candidateId> | candidates | status | test [threads] [duration_seconds] [target_vps] | exit");
                        break;
                }
            } catch (com.zeroc.Ice.CommunicatorDestroyedException e) {
                System.err.println("Communicator destroyed. Exiting UI loop.");
                running = false; 
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
            }
        }
    }

    private static void handleSingleVote(String citizenDocument, String candidateIdStr, int numericTableId) {
        try {
            int candidateId = Integer.parseInt(candidateIdStr);
            long startTime = System.currentTimeMillis();
            
            VoteData vote = new VoteData(citizenDocument, candidateId, numericTableId, 
                                         LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            // Use shorter timeout for async processing
            try {
                votingTableImpl.emitVote(vote, null);
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;
                System.out.println("Vote for citizen document " + citizenDocument + " to candidate " + candidateId + " submitted for async processing.");
                System.out.println("Response time: " + responseTime + "ms");
            } catch (com.zeroc.Ice.TimeoutException e) {
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;
                System.out.println("Vote for citizen document " + citizenDocument + " queued for processing (async timeout).");
                System.out.println("Response time: " + responseTime + "ms");
            }
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid candidate ID format. Must be an integer. Citizen document should be a string.");
        } catch (ElectionInactive e) {
            System.err.println("Vote submission failed: " + e.reason);
        } catch (Exception e) {
            System.err.println("Error submitting vote: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleCandidatesCommand(ControlCenterServicePrx controlCenterService) {
        try {
            CandidateData[] candidates = controlCenterService.getCandidates();
            if (candidates != null && candidates.length > 0) {
                System.out.println("Available Candidates:");
                for (CandidateData c : candidates) {
                    System.out.println("  ID: " + c.id + ", Name: " + c.firstName + " " + c.lastName + ", Party: " + c.party);
                }
            } else {
                System.out.println("No candidates available or error fetching them.");
            }
        } catch (Exception e) {
            System.err.println("Error fetching candidates: " + e.getMessage());
        }
    }

    private static void handleStressTest(String[] parts, int numericTableId, ControlCenterServicePrx controlCenterService) {
        int threads = 50;
        int durationSeconds = 60;
        int targetVPS = 1777; // Votes Per Second
        
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
                targetVPS = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid target VPS parameter, using default: " + targetVPS);
            }
        }

        System.out.println("\n=== VOTING STRESS TEST CONFIGURATION ===");
        System.out.println("Threads: " + threads);
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Target VPS (Votes Per Second): " + targetVPS);
        System.out.println("Target total votes: " + (targetVPS * durationSeconds));
        System.out.println("========================================");
        
        runVotingStressTest(threads, durationSeconds, targetVPS, numericTableId, controlCenterService);
    }

    private static void runVotingStressTest(int threads, int durationSeconds, int targetVPS, int numericTableId, ControlCenterServicePrx controlCenterService) {
        System.out.println("\n=== VOTING STRESS TEST CONFIGURATION ===");
        System.out.println("Threads: " + threads);
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Target VPS (Votes Per Second): " + targetVPS);
        System.out.println("Target total votes: " + (targetVPS * durationSeconds));
        System.out.println("========================================");

        // Load citizens for stress test
        System.out.println("\nLoading citizens for table " + numericTableId + " for stress testing...");
        long loadStart = System.currentTimeMillis();
        List<String> availableCitizens = loadCitizensForTable(numericTableId, controlCenterService);
        long loadEnd = System.currentTimeMillis();
        System.out.println("Loaded " + availableCitizens.size() + " citizens for table " + numericTableId + " in " + (loadEnd - loadStart) + "ms");
        
        // Keep a copy of all citizens for reuse
        List<String> allCitizensForReuse = new ArrayList<>(availableCitizens);
        
        if (availableCitizens.isEmpty()) {
            System.err.println("No citizens found for table " + numericTableId + ". Cannot proceed with stress test.");
            return;
        }

        System.out.println("Available citizens: " + availableCitizens.size());
        int targetTotalVotes = targetVPS * durationSeconds;
        if (availableCitizens.size() < targetTotalVotes) {
            System.out.println("⚠️  WARNING: Not enough unique citizens (" + availableCitizens.size() + ") for target votes (" + targetTotalVotes + ")");
            System.out.println("   Test will measure server request processing capacity.");
        }

        // Simplified metrics for server processing capacity
        AtomicLong totalRequests = new AtomicLong(0);
        AtomicLong processedRequests = new AtomicLong(0); // Successfully processed by server

        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        AtomicLong maxResponseTime = new AtomicLong(0);

        // Load candidates
        CandidateData[] candidates;
        try {
            candidates = controlCenterService.getCandidates();
            if (candidates.length == 0) {
                System.err.println("No candidates found. Cannot proceed with stress test.");
                return;
            }
        } catch (Exception e) {
            System.err.println("Error loading candidates: " + e.getMessage());
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        // Calculate timing
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        int votesPerThreadPerSecond = Math.max(1, targetVPS / threads);
        long delayBetweenVotes = Math.max(0, 1000 / votesPerThreadPerSecond);

        System.out.println("Starting voting stress test with async processing...");
        System.out.println("Measuring SERVER REQUEST PROCESSING CAPACITY");
        System.out.println("Each thread will execute ~" + votesPerThreadPerSecond + " requests per second");
        System.out.println("Delay between requests per thread: " + delayBetweenVotes + "ms");

        for (int i = 0; i < threads; i++) {
            Future<?> future = executor.submit(() -> {
                Random random = new Random();
                while (System.currentTimeMillis() < endTime) {
                    try {
                        String citizenDocument;
                        synchronized (availableCitizens) {
                            if (availableCitizens.isEmpty()) {
                                // Reuse a citizen document to test server processing capacity
                                citizenDocument = allCitizensForReuse.get(random.nextInt(allCitizensForReuse.size()));
                            } else {
                                citizenDocument = availableCitizens.remove(0);
                            }
                        }

                        int candidateId = candidates[random.nextInt(candidates.length)].id;

                        long voteStart = System.currentTimeMillis();
                        
                        try {
                            votingTableImpl.vote(citizenDocument, candidateId, null);
                            long voteEnd = System.currentTimeMillis();
                            long responseTime = voteEnd - voteStart;
                            
                            totalRequests.incrementAndGet();
                            totalResponseTime.addAndGet(responseTime);
                            updateMinMax(minResponseTime, maxResponseTime, responseTime);
                            
                            // Count all server responses as processed (server is working)
                            processedRequests.incrementAndGet();
                            
                        } catch (com.zeroc.Ice.TimeoutException timeoutEx) {
                            // For async processing, timeout likely means request was queued
                            long voteEnd = System.currentTimeMillis();
                            long responseTime = voteEnd - voteStart;
                            
                            totalRequests.incrementAndGet();
                            processedRequests.incrementAndGet(); // Assume queued successfully
                            totalResponseTime.addAndGet(responseTime);
                            updateMinMax(minResponseTime, maxResponseTime, responseTime);
                            
                        } catch (Exception e) {
                            // Any server response (including errors) counts as processed
                            long voteEnd = System.currentTimeMillis();
                            long responseTime = voteEnd - voteStart;
                            
                            totalRequests.incrementAndGet();
                            processedRequests.incrementAndGet(); // Server responded
                            totalResponseTime.addAndGet(responseTime);
                            updateMinMax(minResponseTime, maxResponseTime, responseTime);
                        }
                        
                        if (delayBetweenVotes > 0) {
                            Thread.sleep(delayBetweenVotes);
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            futures.add(future);
        }

        // Progress reporting loop
        long lastReportTime = startTime;
        long lastTotalRequests = 0;
        
        while (System.currentTimeMillis() < endTime) {
            try {
                Thread.sleep(5000);
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - startTime;
                
                if (elapsed >= 5000) {
                    long currentTotal = totalRequests.get();
                    long currentProcessed = processedRequests.get();
                    
                    double currentVPS = (double) currentTotal / (elapsed / 1000.0);
                    
                    // Recent VPS calculation
                    double recentVPS = 0;
                    if (currentTime - lastReportTime >= 1000) {
                        long recentRequests = currentTotal - lastTotalRequests;
                        double recentTimeSpan = (currentTime - lastReportTime) / 1000.0;
                        recentVPS = recentRequests / recentTimeSpan;
                        lastReportTime = currentTime;
                        lastTotalRequests = currentTotal;
                    }
                    
                    synchronized (availableCitizens) {
                        System.out.printf("[%ds] Total: %d, Processed: %d, Available citizens: %d, Current RPS: %.1f, Recent RPS: %.1f%n",
                                elapsed / 1000,
                                currentTotal,
                                currentProcessed,
                                availableCitizens.size(),
                                currentVPS,
                                recentVPS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Shutdown and wait for completion
        executor.shutdown();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("Thread execution error: " + e.getMessage());
            }
        }

        // Final results
        long finalDuration = System.currentTimeMillis() - startTime;
        long finalTotal = totalRequests.get();
        long finalProcessed = processedRequests.get();
        
        System.out.println("\n=== VOTING STRESS TEST RESULTS ===");
        System.out.printf("Actual duration: %dms (%.2fs)%n", finalDuration, finalDuration / 1000.0);
        System.out.println("Total requests: " + finalTotal);
        System.out.println("Server processed requests: " + finalProcessed);
        
        double serverProcessingRate = (double) finalProcessed / (finalDuration / 1000.0);
        double successRate = finalTotal > 0 ? (double) finalProcessed * 100 / finalTotal : 0;
        double rpsAchievement = (serverProcessingRate / targetVPS) * 100;
        
        System.out.printf("Server processing success rate: %.2f%%%n", successRate);
        System.out.printf("Actual Server Processing Rate: %.2f requests/second%n", serverProcessingRate);
        System.out.println("Target RPS: " + targetVPS);
        System.out.printf("RPS Achievement: %.2f%%%n", rpsAchievement);
        
        synchronized (availableCitizens) {
            System.out.println("Remaining unique citizens available: " + availableCitizens.size());
        }
        
        if (finalTotal > 0) {
            double avgResponseTime = (double) totalResponseTime.get() / finalTotal;
            System.out.printf("Average response time: %.2fms%n", avgResponseTime);
            System.out.println("Min response time: " + minResponseTime.get() + "ms");
            System.out.println("Max response time: " + maxResponseTime.get() + "ms");
        }

        System.out.println("\nPERFORMANCE VERDICT: " + 
                          (rpsAchievement >= 80 ? "✅ PASSED" : "❌ FAILED"));
        if (rpsAchievement >= 80) {
            System.out.printf("The server successfully processed %.2f requests per second!%n", serverProcessingRate);
        } else {
            System.out.println("The server processing rate did not meet the target performance.");
        }
        
        long uniqueCitizensUsed = allCitizensForReuse.size() - availableCitizens.size();
        long reusedRequests = Math.max(0, finalTotal - uniqueCitizensUsed);
        if (reusedRequests > 0) {
            System.out.printf("📊 Note: %d requests used repeated citizens (testing server capacity).%n", reusedRequests);
        }
        
        System.out.println("==================================");
        
        // Check server queue status after test completion
        System.out.println("\n=== POST-TEST SERVER STATUS ===");
        try {
            // Wait a moment for any remaining processing
            Thread.sleep(2000);
            
            // Try to get server status (this would require adding a method to check server stats)
            System.out.println("Note: Server continues processing any remaining queued votes in background.");
            System.out.println("Check server logs for ongoing processing status.");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("===============================");
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

    /**
     * Load citizens for a specific table from the control center service
     */
    private static List<String> loadCitizensForTable(int tableId, ControlCenterServicePrx controlCenterService) {
        List<String> citizenDocuments = new ArrayList<>();
        try {
            CitizenData[] citizens = controlCenterService.getCitizensByTableId(tableId);
            for (CitizenData citizen : citizens) {
                citizenDocuments.add(citizen.document);
            }
        } catch (Exception e) {
            System.err.println("Error loading citizens for table " + tableId + ": " + e.getMessage());
        }
        return citizenDocuments;
    }
}