import ElectionSystem.*;
import models.elections.*;
import models.votaciones.Citizen;
import models.votaciones.VotingTable;
import repositories.elections.*;
import repositories.votaciones.*;
import utils.JPAUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.zeroc.Ice.Current;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

public class ServerImpl implements ServerService {
    
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final CitizenRepository citizenRepository;
    private final VotingTableRepository votingTableRepository;

    private Election currentElection;
    List<Candidate> candidates;
    private Map<Integer, List<VotingTable>> votingTablesByStation;
    private final Map<VotingTable, List<Citizen>> citizensByTableCache;
    
    private final Map<String, EventObserverPrx> subscribers = new ConcurrentHashMap<>();
    
    private final BlockingQueue<EntityManager> votingEMPool = new LinkedBlockingQueue<>();
    private static final int VOTING_EM_POOL_SIZE = 5;
    
    private final ExecutorService voteProcessingExecutor;
    private final BlockingQueue<VoteProcessingTask> voteQueue;
    private final BlockingQueue<VoteProcessingTask> pendingQueue;
    private final ExecutorService pendingQueueProcessors;
    private final AtomicLong processedVotes = new AtomicLong(0);
    private final AtomicLong pendingVotes = new AtomicLong(0);
    private final AtomicBoolean isPendingQueueActive = new AtomicBoolean(true);
    
    private static final int VOTE_PROCESSING_THREADS = 40;
    private static final int PENDING_QUEUE_PROCESSORS = 10;
    private static final int VOTE_QUEUE_CAPACITY = 50000;
    private static final int PENDING_QUEUE_CAPACITY = 100000;
    private static final long PENDING_RETRY_INTERVAL_MS = 50;
    
    private static class VoteProcessingTask {
        final VoteData vote;
        final CompletableFuture<Void> resultFuture;
        final long submitTime;
        
        VoteProcessingTask(VoteData vote) {
            this.vote = vote;
            this.resultFuture = new CompletableFuture<>();
            this.submitTime = System.currentTimeMillis();
        }
    }
    
    public ServerImpl(ElectionRepository electionRepository, 
                      CandidateRepository candidateRepository, 
                      VoteRepository voteRepository, 
                      CitizenRepository citizenRepository, 
                      VotingTableRepository votingTableRepository,
                      VotedCitizenRepository votedCitizenRepository) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.voteRepository = voteRepository;
        this.citizenRepository = citizenRepository;
        this.votingTableRepository = votingTableRepository;
        this.citizensByTableCache = new ConcurrentHashMap<>();
        try {
            initElectionBasicData();
        } catch (Throwable t) {
            System.err.println("ServerImpl: CRITICAL ERROR during initialization: " + t.getMessage());
            t.printStackTrace(System.err);
            throw t;
        }
        
        for (int i = 0; i < VOTING_EM_POOL_SIZE; i++) {
            votingEMPool.offer(JPAUtil.getEntityManagerVoting());
        }
        
        this.voteQueue = new LinkedBlockingQueue<>(VOTE_QUEUE_CAPACITY);
        this.pendingQueue = new LinkedBlockingQueue<>(PENDING_QUEUE_CAPACITY);
        this.voteProcessingExecutor = Executors.newFixedThreadPool(VOTE_PROCESSING_THREADS);
        this.pendingQueueProcessors = Executors.newFixedThreadPool(PENDING_QUEUE_PROCESSORS);
        
        for (int i = 0; i < VOTE_PROCESSING_THREADS; i++) {
            voteProcessingExecutor.submit(this::voteProcessingWorker);
        }
        
        for (int i = 0; i < PENDING_QUEUE_PROCESSORS; i++) {
            final int workerId = i;
            pendingQueueProcessors.submit(() -> pendingQueueWorker(workerId));
        }
        
        System.out.println("ServerImpl initialized with async vote processing system");
    }

    private final Map<String, String> votingStationCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100000;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    @Override
    public String findVotingStationByDocument(String document, Current current) {
        if (document == null || document.trim().isEmpty()) {
            return "";
        }
        
        document = document.trim();
        
        String cachedResult = votingStationCache.get(document);
        if (cachedResult != null) {
            cacheHits.incrementAndGet();
            return "NULL_RESULT".equals(cachedResult) ? "" : cachedResult;
        }
        
        cacheMisses.incrementAndGet();
        
        return queryVotingStationDirect(document);
    }
    
    private String queryVotingStationDirect(String document) {
        try {
            Integer tableId = citizenRepository.findVotingTableIdByDocument(document);
            
            String result;
            if (tableId != null) {
                result = "Usted debe votar en la mesa " + tableId + ".";
            } else {
                result = "";
            }

            if (votingStationCache.size() < MAX_CACHE_SIZE) {
                votingStationCache.put(document, result.isEmpty() ? "NULL_RESULT" : result);
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error in optimized query for document " + document + ": " + e.getMessage());
            return "";
        }
    }

    public void initElectionBasicData() {
        System.out.println("Loading election basic data...");

        try {
            Optional<List<Candidate>> candidatesOpt = candidateRepository.findAll();
            this.candidates = candidatesOpt.orElse(new ArrayList<>());
            
            Optional<Election> electionOpt = electionRepository.findById(1);
            this.currentElection = electionOpt.orElse(null);
            
            this.votingTablesByStation = new HashMap<>();
            try {
                Map<Integer, List<VotingTable>> votingTablesMap = votingTableRepository.groupVotingTablesByStation();
                if (votingTablesMap != null) {
                    this.votingTablesByStation = votingTablesMap;
                }
            } catch (Exception e) {
                System.err.println("Error loading voting tables by station: " + e.getMessage());
                this.votingTablesByStation = new HashMap<>();
            }
            
            System.out.println("Election basic data loaded successfully");
            System.out.println("- Candidates: " + candidates.size());
            System.out.println("- Election: " + (currentElection != null ? currentElection.getName() : "None"));
            System.out.println("- Voting stations: " + votingTablesByStation.size());
            
            System.out.println("Direct query cache system initialized (no pre-loading)");
            
        } catch (Exception e) {
            System.err.println("Error loading election basic data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public ElectionData getElectionData(int controlCenterId, Current current) {
        if (currentElection == null) {
            initElectionBasicData();
        }
        return convertElectionToElectionData(currentElection);
    }

    private List<Citizen> getOrLoadCitizensForTable(VotingTable votingTable) {
        List<Citizen> citizens = citizensByTableCache.get(votingTable);
        if (citizens == null) {
            citizens = citizenRepository.findByVotingTableId(votingTable.getId());
            if (citizens != null) {
                citizensByTableCache.put(votingTable, citizens);
            } else {
                citizens = new ArrayList<>();
                citizensByTableCache.put(votingTable, citizens);
            }
        }
        return citizens;
    }

    @Override
    public VotingTableData[] getVotingTablesFromStation(int controlCenterId, Current current) {
        if (currentElection == null || votingTablesByStation == null) {
            initElectionBasicData();
        }
        
        List<VotingTable> votingTablesForStation = votingTablesByStation != null ? 
            votingTablesByStation.get(controlCenterId) : null;
        if (votingTablesForStation == null || votingTablesForStation.isEmpty()) {
            return new VotingTableData[0];
        }
        
        return votingTablesForStation.stream()
            .map(this::convertVotingTableToVotingTableData)
            .toArray(VotingTableData[]::new);
    }

    private EntityManager borrowVotingEM() throws InterruptedException {
        EntityManager em = votingEMPool.poll(100, TimeUnit.MILLISECONDS);
        if (em == null) {
            em = JPAUtil.getEntityManagerVoting();
        }
        return em;
    }
    
    private void returnVotingEM(EntityManager em) {
        if (em != null && em.isOpen()) {
            votingEMPool.offer(em);
        }
    }

    @Override
    public void registerVote(VoteData vote, Current current)
        throws CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable {
        
        if (this.candidates == null) {
            initElectionBasicData();
        }
        
        boolean candidateExists = this.candidates != null && this.candidates.stream()
            .anyMatch(c -> c.getId() == vote.candidateId);
        if (!candidateExists) {
            throw new CandidateNotFound("Candidate with ID " + vote.candidateId + " not found");
        }
        
        VoteProcessingTask task = new VoteProcessingTask(vote);
        
        if (voteQueue.offer(task)) {
            return;
        }
        
        if (pendingQueue.offer(task)) {
            pendingVotes.incrementAndGet();
            return;
        }
        
        long currentProcessed = processedVotes.get();
        throw new RuntimeException("Vote processing system at absolute maximum capacity - " + 
                                 currentProcessed + " votes processed so far.");
    }
    
    private void voteProcessingWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                VoteProcessingTask task = voteQueue.take();
                processVoteAsync(task);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error processing vote: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void pendingQueueWorker(int workerId) {
        while (isPendingQueueActive.get() && !Thread.currentThread().isInterrupted()) {
            try {
                VoteProcessingTask pendingTask = pendingQueue.poll(PENDING_RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
                
                if (pendingTask != null) {
                    if (voteQueue.offer(pendingTask)) {
                        pendingVotes.decrementAndGet();
                    } else {
                        if (!pendingQueue.offer(pendingTask)) {
                            System.err.println("Pending queue overflow - dropping vote task for worker " + workerId);
                        }
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in pending queue worker " + workerId + ": " + e.getMessage());
            }
        }
    }
    
    private void processVoteAsync(VoteProcessingTask task) {
        EntityManager votingEM = null;
        EntityManager electionsEM = null;
        
        try {
            votingEM = borrowVotingEM();
            electionsEM = JPAUtil.getEntityManagerElections();
            
            final EntityManager finalElectionsEM = electionsEM;
            
            JPAUtil.executeInTransactionVoid(votingEM, votingEntityManager -> {
                TypedQuery<Object[]> citizenQuery = votingEntityManager.createQuery(
                    "SELECT c.id, c.votingTable.id FROM Citizen c WHERE c.document = :document", 
                    Object[].class
                );
                citizenQuery.setParameter("document", task.vote.citizenDocument);
                citizenQuery.setHint("org.hibernate.readOnly", true);
                
                List<Object[]> citizenResults = citizenQuery.getResultList();
                if (citizenResults.isEmpty()) {
                    throw new RuntimeException(new CitizenNotFound("Citizen with document " + task.vote.citizenDocument + " not found"));
                }
                
                Object[] citizenResult = citizenResults.get(0);
                Integer citizenId = (Integer) citizenResult[0];
                Integer citizenTableId = (Integer) citizenResult[1];
                
                if (!citizenTableId.equals(task.vote.tableId)) {
                    throw new RuntimeException(new CitizenNotBelongToTable("Citizen with document " + task.vote.citizenDocument + " (ID: " + citizenId + ") does not belong to voting table " + task.vote.tableId));
                }
                
                JPAUtil.executeInTransactionVoid(finalElectionsEM, electionsEntityManager -> {
                    TypedQuery<Long> votedQuery = electionsEntityManager.createQuery(
                        "SELECT COUNT(vc) FROM VotedCitizen vc WHERE vc.citizenId = :citizenId", 
                        Long.class
                    );
                    votedQuery.setParameter("citizenId", citizenId);
                    Long voteCount = votedQuery.getSingleResult();
                    
                    if (voteCount > 0) {
                        throw new RuntimeException(new CitizenAlreadyVoted("Citizen with document " + task.vote.citizenDocument + " (ID: " + citizenId + ") has already voted"));
                    }
                    
                    Candidate candidateEntity = this.candidates.stream()
                        .filter(c -> c.getId() == task.vote.candidateId)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException(new CandidateNotFound("Candidate with ID " + task.vote.candidateId + " not found")));
                    
                    electionsEntityManager.persist(new VotedCitizen(citizenId));
                    
                    Vote newVote = new Vote();
                    newVote.setCandidate(candidateEntity);
                    newVote.setTableId(task.vote.tableId);
                    newVote.setTimestamp(LocalDateTime.now());
                    newVote.setElection(this.currentElection);
                    electionsEntityManager.persist(newVote);
                    
                    electionsEntityManager.flush();
                });
            });
            
            long processingTime = System.currentTimeMillis() - task.submitTime;
            long processed = processedVotes.incrementAndGet();
            
            if (processed % 1000 == 0) {
                System.out.println("Processed " + processed + " votes. Last processing time: " + processingTime + "ms");
            }
            
            task.resultFuture.complete(null);
            
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            boolean isConnectionPoolError = errorMessage.contains("connection pool") || 
                                          errorMessage.contains("maximum size") ||
                                          errorMessage.contains("no connection is currently available") ||
                                          errorMessage.contains("pool has reached");
            
            if (isConnectionPoolError) {
                task.resultFuture.completeExceptionally(e);
            } else {
                System.err.println("Error processing vote for " + task.vote.citizenDocument + ": " + e.getMessage());
                task.resultFuture.completeExceptionally(e);
            }
        } finally {
            if (votingEM != null) {
                returnVotingEM(votingEM);
            }
            if (electionsEM != null && electionsEM.isOpen()) {
                electionsEM.close();
            }
        }
    }
    
    @Override
    public String getProcessingStats(Current current) {
        return String.format("Processed: %d, Primary queue: %d/%d, Pending: %d/%d, Workers: %d+%d, Active: %s", 
                           processedVotes.get(), 
                           voteQueue.size(), VOTE_QUEUE_CAPACITY,
                           pendingQueue.size(), PENDING_QUEUE_CAPACITY,
                           VOTE_PROCESSING_THREADS, PENDING_QUEUE_PROCESSORS,
                           isPendingQueueActive.get());
    }

    @Override
    public void printQueueStatus(Current current) {
        System.out.println("=== QUEUE STATUS ===");
        System.out.println("Processed votes: " + processedVotes.get());
        System.out.println("Primary queue: " + voteQueue.size() + "/" + VOTE_QUEUE_CAPACITY + 
                         " (" + String.format("%.1f", (double) voteQueue.size() / VOTE_QUEUE_CAPACITY * 100) + "%)");
        System.out.println("Pending queue: " + pendingQueue.size() + "/" + PENDING_QUEUE_CAPACITY + 
                         " (" + String.format("%.1f", (double) pendingQueue.size() / PENDING_QUEUE_CAPACITY * 100) + "%)");
        System.out.println("Vote processing workers: " + VOTE_PROCESSING_THREADS + " active");
        System.out.println("Pending queue workers: " + PENDING_QUEUE_PROCESSORS + " active");
        System.out.println("Pending queue active: " + isPendingQueueActive.get());
        System.out.println("===================");
    }

    public void shutdown() {
        System.out.println("Initiating ServerImpl shutdown...");
        
        isPendingQueueActive.set(false);
        
        voteProcessingExecutor.shutdown();
        pendingQueueProcessors.shutdown();
        
        try {
            if (!voteProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("Force shutting down vote processing executor");
                voteProcessingExecutor.shutdownNow();
            }
            
            if (!pendingQueueProcessors.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("Force shutting down pending queue processors");
                pendingQueueProcessors.shutdownNow();
            }
            
            System.out.println("ServerImpl shutdown completed");
            
        } catch (InterruptedException e) {
            System.out.println("Shutdown interrupted, forcing immediate shutdown");
            voteProcessingExecutor.shutdownNow();
            pendingQueueProcessors.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ElectionData convertElectionToElectionData(Election election) {
        return new ElectionData(
            election.getName(),
            election.getStartTime().format(DateTimeFormatter.ISO_DATE_TIME),
            election.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME),
            this.candidates.stream()
                .map(this::convertCandidateToCandidateData)
                .collect(Collectors.toList()).toArray(new CandidateData[0])
        );
    }

    private VotingTableData convertVotingTableToVotingTableData(VotingTable votingTable) {
        List<Citizen> citizensInTable = getOrLoadCitizensForTable(votingTable);
        
        return new VotingTableData(
            votingTable.getId(),
            citizensInTable.stream()
                .map(this::convertCitizenToCitizenData)
                .collect(Collectors.toList()).toArray(new CitizenData[0])
        );
    }

    private CitizenData convertCitizenToCitizenData(Citizen citizen) {
        return new CitizenData(
            citizen.getId(),
            citizen.getDocument(),
            citizen.getFirstName(),
            citizen.getLastName(),
            citizen.getVotingTable().getId()
        );
    }

    @Override
    public CandidateData[] getCandidates(Current current) {
        if (this.candidates == null) {
            initElectionBasicData();
        }
        
        if (this.candidates == null) {
            return new CandidateData[0];
        }
        
        return this.candidates.stream()
            .map(this::convertCandidateToCandidateData)
            .collect(Collectors.toList()).toArray(new CandidateData[0]);
    }

    private CandidateData convertCandidateToCandidateData(Candidate candidate) {
        return new CandidateData(
            candidate.getId(),
            candidate.getFirstName(),
            candidate.getLastName(),
            candidate.getParty()
        );
    }

    @Override
    public String getCacheStats(Current current) {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        return String.format("Cache: %d entries, %.1f%% hit rate (%d hits, %d misses)",
                votingStationCache.size(), hitRate, hits, misses);
    }
    
    // Method to clear cache when necessary
    @Override
    public void clearCache(Current current) {
        votingStationCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        System.out.println("Voting station cache cleared");
    }

    @Override
    public CandidateResult[] getGlobalResults(Current current) {
        if (this.candidates == null) {
            initElectionBasicData();
        }
        
        if (this.candidates == null) {
            return new CandidateResult[0];
        }
        
        Map<Integer, Long> votesByCandidate = candidates.stream().collect(Collectors.toMap(
            Candidate::getId,
            c -> voteRepository.countByCandidateId(c.getId())
        ));

        return candidates.stream()
            .map(c -> new CandidateResult(
                c.getId(),
                c.getFirstName() + " " + c.getLastName(),
                votesByCandidate.getOrDefault(c.getId(), 0L).intValue()
            )).toArray(CandidateResult[]::new);
    }

    @Override
    public Map<Integer, CandidateResult[]> getResultsByVotingTable(Current current) {
        if (this.candidates == null) {
            initElectionBasicData();
        }
        
        Map<Integer, Map<Integer, Integer>> rawVotes = voteRepository.countVotesGroupedByTableAndCandidate();

        Map<Integer, CandidateResult[]> tableResults = new HashMap<>();
        if (this.candidates != null) {
            for (Map.Entry<Integer, Map<Integer, Integer>> entry : rawVotes.entrySet()) {
                int tableId = entry.getKey();
                Map<Integer, Integer> candidateVotes = entry.getValue();

                List<CandidateResult> results = new ArrayList<>();
                for (Candidate c : candidates) {
                    int count = candidateVotes.getOrDefault(c.getId(), 0);
                    results.add(new CandidateResult(c.getId(), c.getFirstName() + " " + c.getLastName(), count));
                }
                tableResults.put(tableId, results.toArray(new CandidateResult[0]));
            }
        }

        return tableResults;
    }

    @Override
    public CitizenData[] getCitizensByTableId(int tableId, Current current) {
        try {
            List<Citizen> citizens = citizenRepository.findByVotingTableId(tableId);
            return citizens.stream()
                .map(this::convertCitizenToCitizenData)
                .toArray(CitizenData[]::new);
        } catch (Exception e) {
            System.err.println("Error getting citizens for table " + tableId + ": " + e.getMessage());
            return new CitizenData[0];
        }
    }

    @Override
    public void subscribe(EventObserverPrx observer, String subscriberId, Current current) {
        if (observer == null) {
            System.err.println("Cannot subscribe: observer is null");
            return;
        }
        
        if (subscriberId == null || subscriberId.trim().isEmpty()) {
            System.err.println("Cannot subscribe: subscriber ID is null or empty");
            return;
        }

        subscribers.put(subscriberId, observer);
        System.out.println("Subscriber " + subscriberId + " registered successfully");
        
        if (this.currentElection != null) {
            try {
                Map<String, String> details = new HashMap<>();
                details.put("electionId", String.valueOf(this.currentElection.getId()));
                details.put("electionName", this.currentElection.getName());
                details.put("startTime", this.currentElection.getStartTime().format(DateTimeFormatter.ISO_DATE_TIME));
                details.put("endTime", this.currentElection.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME));
                
                ElectionEvent welcomeEvent = new ElectionEvent(
                    EventType.ElectionStarted,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                    details
                );
                
                CompletableFuture.runAsync(() -> {
                    try {
                        observer._notify(welcomeEvent);
                    } catch (Exception e) {
                        System.err.println("Failed to send welcome event to subscriber " + subscriberId + ": " + e.getMessage());
                        subscribers.remove(subscriberId);
                    }
                });
                
            } catch (Exception e) {
                System.err.println("Error creating welcome event for subscriber " + subscriberId + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void unsubscribe(String subscriberId, Current current) {
        if (subscriberId == null || subscriberId.trim().isEmpty()) {
            System.err.println("Invalid subscriber ID for unsubscribe: " + subscriberId);
            return;
        }

        boolean removed = subscribers.remove(subscriberId) != null;
        if (removed) {
            System.out.println("Subscriber " + subscriberId + " unsubscribed successfully");
        } else {
            System.out.println("Subscriber " + subscriberId + " was not found in the subscription list");
        }
    }
}