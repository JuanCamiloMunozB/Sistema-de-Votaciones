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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Queue; 
import java.util.LinkedList; 

public class VotingTableMain {
    
    private static VotingTableImpl votingTableImpl;
    private static final int[] TEST_CANDIDATE_IDS = {1, 2, 3, 4};

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
            votingTableImpl.emitVote(vote, null); 
            
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            System.out.println("Vote for citizen document " + citizenDocument + " to candidate " + candidateId + " submitted.");
            System.out.println("Response time: " + responseTime + "ms");
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
        int threads = 20;
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
        System.out.println("========================================\n");
        
        runVotingStressTest(threads, durationSeconds, targetVPS, numericTableId, controlCenterService);
    }

    private static void runVotingStressTest(int threads, int durationSeconds, int targetVPS, int numericTableId, ControlCenterServicePrx controlCenterService) {
        // Cargar ciudadanos internamente para la prueba de estrés
        System.out.println("Loading citizens for table " + numericTableId + " for stress testing...");
        Queue<String> availableCitizenDocuments = new LinkedList<>();
        Object citizenQueueLock = new Object();
        
        try {
            long loadStartTime = System.currentTimeMillis();
            CitizenData[] citizens = controlCenterService.getCitizensByTableId(numericTableId);
            synchronized (citizenQueueLock) {
                for (CitizenData citizen : citizens) {
                    availableCitizenDocuments.offer(citizen.document);
                }
            }
            long loadEndTime = System.currentTimeMillis();
            System.out.println("Loaded " + citizens.length + " citizens for table " + numericTableId + " in " + (loadEndTime - loadStartTime) + "ms");
            
            if (availableCitizenDocuments.isEmpty()) {
                System.err.println("ERROR: No citizens found for table " + numericTableId + ". Cannot perform stress test.");
                return;
            }
            
            int availableCitizens = availableCitizenDocuments.size();
            int estimatedVotesNeeded = targetVPS * durationSeconds;
            
            System.out.println("Available citizens: " + availableCitizens);
            if (availableCitizens < estimatedVotesNeeded) {
                System.out.println("⚠️  WARNING: Not enough unique citizens (" + availableCitizens + ") for target votes (" + estimatedVotesNeeded + ")");
                System.out.println("   Test will be limited by available citizens.");
            }
            
        } catch (Exception e) {
            System.err.println("Error loading citizens for table " + numericTableId + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger totalVotes = new AtomicInteger(0);
        AtomicInteger successfulVotes = new AtomicInteger(0);
        AtomicInteger failedVotes = new AtomicInteger(0);
        AtomicInteger citizenNotBelongErrors = new AtomicInteger(0);
        AtomicInteger citizenAlreadyVotedErrors = new AtomicInteger(0);
        AtomicInteger citizenNotFoundErrors = new AtomicInteger(0);
        AtomicInteger electionInactiveErrors = new AtomicInteger(0);
        AtomicInteger noCitizensAvailableErrors = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        AtomicLong maxResponseTime = new AtomicLong(0);
        
        // Rate limiting: votes per second per thread
        int votesPerThreadPerSecond = Math.max(1, targetVPS / threads);
        long delayBetweenVotes = Math.max(1, 1000 / votesPerThreadPerSecond); // milliseconds
        
        System.out.println("Starting voting stress test...");
        System.out.println("Each thread will execute ~" + votesPerThreadPerSecond + " votes per second");
        System.out.println("Delay between votes per thread: " + delayBetweenVotes + "ms");
        
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
                        // Obtener documento real de la queue local
                        String citizenDocument;
                        synchronized (citizenQueueLock) {
                            citizenDocument = availableCitizenDocuments.poll();
                        }
                        
                        if (citizenDocument == null) {
                            noCitizensAvailableErrors.incrementAndGet();
                            failedVotes.incrementAndGet();
                            totalVotes.incrementAndGet();
                            
                            // Esperar un poco más si no hay ciudadanos disponibles
                            Thread.sleep(100);
                            continue;
                        }
                        
                        int candidateId = TEST_CANDIDATE_IDS[random.nextInt(TEST_CANDIDATE_IDS.length)];
                        
                        long voteStart = System.currentTimeMillis();
                        
                        int result = votingTableImpl.vote(citizenDocument, candidateId, null);
                        
                        long voteEnd = System.currentTimeMillis();
                        long responseTime = voteEnd - voteStart;
                        
                        totalVotes.incrementAndGet();
                        totalResponseTime.addAndGet(responseTime);
                        updateMinMax(minResponseTime, maxResponseTime, responseTime);
                        
                        // Classify result
                        switch (result) {
                            case 0:
                                successfulVotes.incrementAndGet();
                                // No devolver el documento si el voto fue exitoso (ciudadano ya votó)
                                break;
                            case 1:
                                citizenNotBelongErrors.incrementAndGet();
                                failedVotes.incrementAndGet();
                                // Devolver documento a la queue para reintento
                                synchronized (citizenQueueLock) {
                                    availableCitizenDocuments.offer(citizenDocument);
                                }
                                break;
                            case 2:
                                citizenAlreadyVotedErrors.incrementAndGet();
                                failedVotes.incrementAndGet();
                                // No devolver el documento (ciudadano ya votó)
                                break;
                            case 3:
                                citizenNotFoundErrors.incrementAndGet();
                                failedVotes.incrementAndGet();
                                // Devolver documento a la queue para reintento
                                synchronized (citizenQueueLock) {
                                    availableCitizenDocuments.offer(citizenDocument);
                                }
                                break;
                            default:
                                failedVotes.incrementAndGet();
                                // Devolver documento a la queue para reintento
                                synchronized (citizenQueueLock) {
                                    availableCitizenDocuments.offer(citizenDocument);
                                }
                                break;
                        }
                        
                        if (delayBetweenVotes > 0) {
                            Thread.sleep(delayBetweenVotes);
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // Check if it's an election inactive situation
                        if (!votingTableImpl.isElectionActive()) {
                            electionInactiveErrors.incrementAndGet();
                        }
                        failedVotes.incrementAndGet();
                        totalVotes.incrementAndGet();
                        
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });
            futures.add(future);
        }
        
        monitorVotingProgress(totalVotes, successfulVotes, failedVotes, citizenNotBelongErrors, 
                            citizenAlreadyVotedErrors, citizenNotFoundErrors, electionInactiveErrors, 
                            noCitizensAvailableErrors, availableCitizenDocuments, citizenQueueLock, 
                            startTime, durationSeconds);
        
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted while waiting for completion");
            } catch (ExecutionException e) {
                System.err.println("Thread execution error: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        
        // Calculate final results
        long actualDuration = System.currentTimeMillis() - startTime;
        int total = totalVotes.get();
        int successful = successfulVotes.get();
        int failed = failedVotes.get();
        int notBelong = citizenNotBelongErrors.get();
        int alreadyVoted = citizenAlreadyVotedErrors.get();
        int notFound = citizenNotFoundErrors.get();
        int electionInactive = electionInactiveErrors.get();
        int noCitizensAvailable = noCitizensAvailableErrors.get();
        double actualVPS = (double) total / (actualDuration / 1000.0);
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        double avgResponseTime = total > 0 ? (double) totalResponseTime.get() / total : 0;
        
        int remainingCitizens;
        synchronized (citizenQueueLock) {
            remainingCitizens = availableCitizenDocuments.size();
        }
        
        System.out.println("\n=== VOTING STRESS TEST RESULTS ===");
        System.out.println("Actual duration: " + actualDuration + "ms (" + (actualDuration / 1000.0) + "s)");
        System.out.println("Total votes: " + total);
        System.out.println("Successful votes: " + successful);
        System.out.println("Failed votes: " + failed);
        System.out.println("  - Citizen not belong to table: " + notBelong);
        System.out.println("  - Citizen already voted: " + alreadyVoted);
        System.out.println("  - Citizen not found: " + notFound);
        System.out.println("  - Election inactive: " + electionInactive);
        System.out.println("  - No citizens available: " + noCitizensAvailable);
        System.out.println("Success rate: " + String.format("%.2f%%", successRate));
        System.out.println("Actual VPS: " + String.format("%.2f", actualVPS));
        System.out.println("Target VPS: " + targetVPS);
        System.out.println("VPS Achievement: " + String.format("%.2f%%", (actualVPS / targetVPS) * 100));
        System.out.println("Remaining citizens available: " + remainingCitizens);
        
        if (total > 0) {
            System.out.println("Average response time: " + String.format("%.2fms", avgResponseTime));
            System.out.println("Min response time: " + minResponseTime.get() + "ms");
            System.out.println("Max response time: " + maxResponseTime.get() + "ms");
        }
        
        // Performance verdict
        boolean targetAchieved = actualVPS >= (targetVPS * 0.95);
        System.out.println("\nPERFORMANCE VERDICT: " + (targetAchieved ? "✅ PASSED" : "❌ FAILED"));
        
        if (targetAchieved) {
            System.out.println("The voting system successfully handled the target load!");
        } else {
            System.out.println("The voting system did not meet the target performance.");
            System.out.println("Consider checking election status or optimizing the system.");
        }
        
        // Error analysis
        if (electionInactive > 0) {
            System.out.println("⚠️  Note: " + electionInactive + " votes failed due to election being inactive.");
            System.out.println("   Make sure the election is active for accurate performance testing.");
        }
        
        if (noCitizensAvailable > 0) {
            System.out.println("⚠️  Note: " + noCitizensAvailable + " votes failed due to no citizens available.");
            System.out.println("   This may indicate all valid citizens have already voted.");
        }
        
        if (notBelong > 0 || alreadyVoted > 0 || notFound > 0) {
            System.out.println("ℹ️  Error breakdown:");
            if (notBelong > 0) System.out.println("   - Citizens not belonging to table: " + notBelong + " (" + String.format("%.1f%%", (double)notBelong/total*100) + ")");
            if (alreadyVoted > 0) System.out.println("   - Citizens already voted: " + alreadyVoted + " (" + String.format("%.1f%%", (double)alreadyVoted/total*100) + ")");
            if (notFound > 0) System.out.println("   - Citizens not found: " + notFound + " (" + String.format("%.1f%%", (double)notFound/total*100) + ")");
        }
        
        System.out.println("==================================\n");
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
    
    private static void monitorVotingProgress(AtomicInteger totalVotes, AtomicInteger successfulVotes, 
                                            AtomicInteger failedVotes, AtomicInteger citizenNotBelongErrors,
                                            AtomicInteger citizenAlreadyVotedErrors, AtomicInteger citizenNotFoundErrors,
                                            AtomicInteger electionInactiveErrors, AtomicInteger noCitizensAvailableErrors,
                                            Queue<String> availableCitizenDocuments, Object citizenQueueLock,
                                            long startTime, int durationSeconds) {
        Thread progressMonitor = new Thread(() -> {
            try {
                int lastTotal = 0;
                for (int i = 0; i < durationSeconds; i += 5) {
                    Thread.sleep(5000);
                    
                    long elapsed = System.currentTimeMillis() - startTime;
                    int currentTotal = totalVotes.get();
                    int currentSuccessful = successfulVotes.get();
                    int currentFailed = failedVotes.get();
                    int currentNotBelong = citizenNotBelongErrors.get();
                    int currentAlreadyVoted = citizenAlreadyVotedErrors.get();
                    int currentNotFound = citizenNotFoundErrors.get();
                    int currentInactive = electionInactiveErrors.get();
                    int currentNoCitizens = noCitizensAvailableErrors.get();
                    
                    int availableCitizens;
                    synchronized (citizenQueueLock) {
                        availableCitizens = availableCitizenDocuments.size();
                    }
                    
                    double currentVPS = (double) currentTotal / (elapsed / 1000.0);
                    double recentVPS = (double) (currentTotal - lastTotal) / 5.0;
                    
                    System.out.printf("[%ds] Total: %d, Success: %d, Failed: %d (NotBelong: %d, AlreadyVoted: %d, NotFound: %d, Inactive: %d, NoCitizens: %d), Available: %d, Current VPS: %.1f, Recent VPS: %.1f%n",
                            elapsed / 1000, currentTotal, currentSuccessful, currentFailed, 
                            currentNotBelong, currentAlreadyVoted, currentNotFound, currentInactive, currentNoCitizens,
                            availableCitizens, currentVPS, recentVPS);
                    
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