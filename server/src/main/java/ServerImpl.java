import ElectionSystem.*;
import models.elections.*;
import models.votaciones.Citizen;
import models.votaciones.VotingStation;
import models.votaciones.VotingTable;
import repositories.elections.*;
import repositories.votaciones.*;

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
import java.util.stream.Collectors;

import com.zeroc.Ice.Current;
import com.zeroc.Ice.TimeoutException;
import com.zeroc.Ice.ConnectFailedException;

public class ServerImpl implements ServerService {
    
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final CitizenRepository citizenRepository;
    private final VotingTableRepository votingTableRepository;
    private final VotedCitizenRepository votedCitizenRepository;

    private Election currentElection;
    List<Candidate> candidates;
    private Map<Integer, List<VotingTable>> votingTablesByStation;
    private final Map<VotingTable, List<Citizen>> citizensByTableCache;
    
    private final Map<String, EventObserverPrx> subscribers = new ConcurrentHashMap<>();
    private static final int NOTIFICATION_TIMEOUT_SECONDS = 2;
    
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
        this.votedCitizenRepository = votedCitizenRepository;
        this.citizensByTableCache = new ConcurrentHashMap<>();
        try {
            initElectionBasicData();
        } catch (Throwable t) {
            System.err.println("ServerImpl: CRITICAL ERROR during initialization: " + t.getMessage());
            t.printStackTrace(System.err);
            throw t;
        }
    }

    private void initElectionBasicData() {
        if (this.currentElection != null) {
            return;
        }
        this.currentElection = electionRepository.findCurrentElection()
            .orElseThrow(() -> new RuntimeException("No current election found"));
        this.candidates = candidateRepository.findCandidatesByElectionId(this.currentElection.getId());
        this.votingTablesByStation = votingTableRepository.groupVotingTablesByStation();
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
        if (currentElection == null) {
            initElectionBasicData();
        }
        
        List<VotingTable> votingTablesForStation = votingTablesByStation.get(controlCenterId);
        if (votingTablesForStation == null || votingTablesForStation.isEmpty()) {
            return new VotingTableData[0];
        }
        
        return votingTablesForStation.stream()
            .map(this::convertVotingTableToVotingTableData)
            .toArray(VotingTableData[]::new);
    }

    @Override
    public void registerVote(VoteData vote, Current current)
        throws CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable {
        if (this.candidates == null || this.votingTablesByStation == null) {
            initElectionBasicData();
        }

        Citizen citizen = citizenRepository.findByDocument(vote.citizenDocument)
            .orElseThrow(() -> new CitizenNotFound("Citizen with document " + vote.citizenDocument + " not found"));
        if (votedCitizenRepository.existsById(citizen.getId())) {
            throw new CitizenAlreadyVoted("Citizen with document " + vote.citizenDocument + " (ID: " + citizen.getId() + ") has already voted");
        }

        if (citizen.getVotingTable().getId() != vote.tableId) {
            throw new CitizenNotBelongToTable("Citizen with document " + vote.citizenDocument + " (ID: " + citizen.getId() + ") does not belong to voting table " + vote.tableId);
        }
        
        Candidate candidateEntity = this.candidates.stream()
            .filter(c -> c.getId() == vote.candidateId)
            .findFirst()
            .orElseThrow(() -> new CandidateNotFound("Candidate with ID " + vote.candidateId + " not found"));

        votedCitizenRepository.save(new VotedCitizen(citizen.getId()));

        Vote newVoteToSave = new Vote();
        newVoteToSave.setCandidate(candidateEntity);
        newVoteToSave.setTableId(vote.tableId);
        newVoteToSave.setTimestamp(java.time.LocalDateTime.now());
        if (this.currentElection != null) {
            newVoteToSave.setElection(this.currentElection);
        }

        voteRepository.save(newVoteToSave);
        System.out.println("Vote registered for citizen: " + vote.citizenDocument);

        Map<String, String> details = new HashMap<>();
        details.put("citizenDocument", vote.citizenDocument);
        details.put("citizenId", String.valueOf(citizen.getId()));
        details.put("candidateId", String.valueOf(vote.candidateId));
        details.put("tableId", String.valueOf(vote.tableId));
        if (this.currentElection != null) {
            details.put("electionId", String.valueOf(this.currentElection.getId()));
        }
        details.put("originalTimestamp", vote.timestamp);

        ElectionEvent event = new ElectionEvent(
            EventType.VoteRegistered,
            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            details
        );
        notifySubscribers(event);
    }

    @Override
    public void subscribe(EventObserverPrx observer, String observerIdentity, Current current) {
        if (observer == null || observerIdentity == null || observerIdentity.isEmpty()) {
            System.err.println("Subscribe attempt with null observer or empty identity.");
            return;
        }

        try {
            observer.ice_ping();
            subscribers.put(observerIdentity, observer);
            System.out.println("Observer '" + observerIdentity + "' subscribed successfully.");
        } catch (Exception e) {
            System.err.println("Failed to subscribe observer '" + observerIdentity + "': " + e.getMessage());
        }
    }

    @Override
    public void unsubscribe(String observerIdentity, Current current) {
        if (observerIdentity == null || observerIdentity.isEmpty()) {
            return;
        }
        EventObserverPrx removed = subscribers.remove(observerIdentity);
        if (removed != null) {
            System.out.println("Observer '" + observerIdentity + "' unsubscribed.");
        }
    }

    private void notifySubscribers(ElectionEvent event) {
        List<Map.Entry<String, EventObserverPrx>> toNotify = new ArrayList<>(subscribers.entrySet());

        for (Map.Entry<String, EventObserverPrx> entry : toNotify) {
            String identity = entry.getKey();
            EventObserverPrx observer = entry.getValue();
            
            CompletableFuture.runAsync(() -> {
                try {
                    if (observer == null) {
                        subscribers.remove(identity);
                        return;
                    }
                    
                    EventObserverPrx timeoutObserver = observer.ice_timeout(NOTIFICATION_TIMEOUT_SECONDS * 1000);
                    timeoutObserver._notify(event);
                    
                } catch (com.zeroc.Ice.ObjectNotExistException e) {
                    subscribers.remove(identity);
                } catch (com.zeroc.Ice.CommunicatorDestroyedException e) {
                    subscribers.remove(identity);
                } catch (TimeoutException e) {
                    System.err.println("Timeout notifying observer '" + identity + "'");
                } catch (ConnectFailedException e) {
                    subscribers.remove(identity);
                } catch (Exception e) {
                    System.err.println("Failed to notify observer '" + identity + "': " + e.getMessage());
                }
            }).orTimeout(NOTIFICATION_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS)
            .exceptionally(throwable -> null);
        }
    }

    private ElectionData convertElectionToElectionData(Election election) {
        return new ElectionData(
            election.getName(),
            election.getStartTime().format(DateTimeFormatter.ISO_DATE_TIME),
            election.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME),
            this.candidates.stream()
                .map(this::convertCandidateToCandidateData)
                .toList().toArray(new CandidateData[0])
        );
    }

    private VotingTableData convertVotingTableToVotingTableData(VotingTable votingTable) {
        List<Citizen> citizensInTable = getOrLoadCitizensForTable(votingTable);
        
        return new VotingTableData(
            votingTable.getId(),
            citizensInTable.stream()
                .map(this::convertCitizenToCitizenData)
                .toList().toArray(new CitizenData[0])
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
        return this.candidates.stream()
            .map(this::convertCandidateToCandidateData)
            .toList().toArray(new CandidateData[0]);
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
    public String findVotingStationByDocument(String document, Current current) {
        try {
            Optional<Citizen> citizenOpt = citizenRepository.findByDocument(document);
            
            if (citizenOpt.isPresent()) {
                Citizen citizen = citizenOpt.get();
                VotingStation station = citizen.getVotingTable().getVotingStation();
                
                return String.format(
                    "Usted debe votar en %s ubicado en %s en %s, %s en la mesa %d.",
                    station.getName(),
                    station.getAddress(),
                    station.getMunicipality().getName(),
                    station.getMunicipality().getDepartment().getName(),
                    citizen.getVotingTable().getId()
                );
            } else {
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error finding voting station for document " + document + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public CandidateResult[] getGlobalResults(Current current) {
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
    Map<Integer, Map<Integer, Integer>> rawVotes = voteRepository.countVotesGroupedByTableAndCandidate();

    Map<Integer, CandidateResult[]> tableResults = new HashMap<>();
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
}