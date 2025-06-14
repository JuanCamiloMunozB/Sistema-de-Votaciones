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

import com.zeroc.Ice.Current;

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
    
    public ServerImpl(ElectionRepository electionRepository, 
                      CandidateRepository candidateRepository, 
                      VoteRepository voteRepository, 
                      CitizenRepository citizenRepository, 
                      VotingTableRepository votingTableRepository,
                      VotedCitizenRepository votedCitizenRepository) {
        System.out.println("ServerImpl: Constructor - START");
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.voteRepository = voteRepository;
        this.citizenRepository = citizenRepository;
        this.votingTableRepository = votingTableRepository;
        this.votedCitizenRepository = votedCitizenRepository;
        this.citizensByTableCache = new ConcurrentHashMap<>();
        System.out.println("ServerImpl: Constructor - Repositories assigned.");
        try {
            initElectionBasicData();
        } catch (Throwable t) {
            System.err.println("ServerImpl: CRITICAL ERROR during initElectionBasicData: " + t.getMessage());
            t.printStackTrace(System.err);
            throw t;
        }
        System.out.println("ServerImpl: Constructor - END");
    }

    private void initElectionBasicData() {
        System.out.println("ServerImpl.initElectionBasicData: START");
        if (this.currentElection != null) {
            System.out.println("ServerImpl.initElectionBasicData: Already initialized, returning.");
            return;
        }
        System.out.println("ServerImpl.initElectionBasicData: Finding current election...");
        this.currentElection = electionRepository.findCurrentElection()
            .orElseThrow(() -> {
                System.err.println("ServerImpl.initElectionBasicData: No current election found - RuntimeException.");
                return new RuntimeException("No current election found");
            });
        System.out.println("ServerImpl.initElectionBasicData: Current election found: " + this.currentElection.getName());
        
        System.out.println("ServerImpl.initElectionBasicData: Finding candidates...");
        this.candidates = candidateRepository.findCandidatesByElectionId(this.currentElection.getId());
        System.out.println("ServerImpl.initElectionBasicData: Candidates found: " + (this.candidates != null ? this.candidates.size() : "null"));

        System.out.println("ServerImpl.initElectionBasicData: Grouping voting tables by station...");
        this.votingTablesByStation = votingTableRepository.groupVotingTablesByStation();
        System.out.println("ServerImpl.initElectionBasicData: Voting tables grouped: " + (this.votingTablesByStation != null ? this.votingTablesByStation.size() : "null"));

        System.out.println("ServerImpl.initElectionBasicData: END - Basic data loaded. Citizens will be loaded on demand.");
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
            System.out.println("[ServerImpl] Cache miss for citizens of table ID: " + votingTable.getId() + ". Loading from repository...");
            citizens = citizenRepository.findByVotingTableId(votingTable.getId());
            if (citizens != null) {
                System.out.println("[ServerImpl] Loaded " + citizens.size() + " citizens for table ID: " + votingTable.getId());
                citizensByTableCache.put(votingTable, citizens);
            } else {
                System.out.println("[ServerImpl] No citizens found in repository for table ID: " + votingTable.getId());
                citizens = new ArrayList<>();
                citizensByTableCache.put(votingTable, citizens);
            }
        } else {
            System.out.println("[ServerImpl] Cache hit for citizens of table ID: " + votingTable.getId() + ". Found " + citizens.size() + " citizens.");
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
            System.out.println("[ServerImpl] No voting tables found for station/controlCenterId: " + controlCenterId);
            return new VotingTableData[0];
        }

        System.out.println("[ServerImpl] Preparing VotingTableData for " + votingTablesForStation.size() + " tables in station: " + controlCenterId);
        
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
        System.out.println("Anonymous vote registered. Citizen with document: " + vote.citizenDocument + " (ID: " + citizen.getId() + ") marked as voted.");

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
        System.out.println("ServerImpl.registerVote: Exiting successfully for document " + vote.citizenDocument);
    }

    @Override
    public void subscribe(EventObserverPrx observer, String observerIdentity, Current current) {
        if (observer == null || observerIdentity == null || observerIdentity.isEmpty()) {
            System.err.println("Subscribe attempt with null observer or empty identity.");
            return;
        }

        subscribers.put(observerIdentity, observer);
        System.out.println("Observer '" + observerIdentity + "' subscribed.");
    }

    @Override
    public void unsubscribe(String observerIdentity, Current current) {
        if (observerIdentity == null || observerIdentity.isEmpty()) {
            System.err.println("Unsubscribe attempt with empty identity.");
            return;
        }
        EventObserverPrx removed = subscribers.remove(observerIdentity);
        if (removed != null) {
            System.out.println("Observer '" + observerIdentity + "' unsubscribed.");
        } else {
            System.out.println("Observer '" + observerIdentity + "' not found for unsubscription.");
        }
    }

    private void notifySubscribers(ElectionEvent event) {
        System.out.println("Notifying " + subscribers.size() + " subscribers of event: " + event.type);
        List<Map.Entry<String, EventObserverPrx>> toNotify = new ArrayList<>(subscribers.entrySet());

        for (Map.Entry<String, EventObserverPrx> entry : toNotify) {
            String identity = entry.getKey();
            EventObserverPrx observer = entry.getValue();
            try {
                observer.notifyEvent(event);
                System.out.println("Successfully notified observer: " + identity);
            } catch (com.zeroc.Ice.ObjectNotExistException | com.zeroc.Ice.CommunicatorDestroyedException e) {
                System.err.println("Observer '" + identity + "' seems to be down. Removing: " + e.getMessage());
                subscribers.remove(identity);
            } catch (Exception e) {
                System.err.println("Failed to notify observer '" + identity + "': " + e.getMessage());
            }
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
        System.out.println("ServerImpl.getCandidates: Called from client. Current candidates: " + (this.candidates != null ? this.candidates.size() : "null"));
        
        if (this.candidates == null) {
            System.out.println("ServerImpl.getCandidates: Candidates is null, initializing...");
            initElectionBasicData();
        }
        
        CandidateData[] result = this.candidates.stream()
            .map(this::convertCandidateToCandidateData)
            .toList().toArray(new CandidateData[0]);
            
        System.out.println("ServerImpl.getCandidates: Returning " + result.length + " candidates");
        return result;
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
                
                String locationInfo = String.format(
                    "Sitio: %s | Dirección: %s | Municipio: %s | Departamento: %s | Código: %d",
                    station.getName(),
                    station.getAddress(),
                    station.getMunicipality().getName(),
                    station.getMunicipality().getDepartment().getName(),
                    station.getConsecutive()
                );
                
                return locationInfo;
            } else {
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("ServerServiceImpl.findVotingStationByDocument: Error for document " + document + ": " + e.getMessage());
            return null;
        }
    }
}