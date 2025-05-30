import ElectionSystem.*;
import models.elections.*;
import models.votaciones.Citizen;
import models.votaciones.VotingTable;
import repositories.elections.*;
import repositories.votaciones.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.zeroc.Ice.Current;

public class ServerImpl implements ServerService {
    
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final CitizenRepository citizenRepository;
    private final VotingTableRepository votingTableRepository;

    private Election currentElection;
    List<Candidate> candidates;
    private Map<Integer, List<VotingTable>> votingTablesByStation;
    private Map<Integer, List<Citizen>> citizensByTable;
    
    private final Map<String, EventObserverPrx> subscribers = new ConcurrentHashMap<>();
    
    public ServerImpl(ElectionRepository electionRepository, 
                      CandidateRepository candidateRepository, 
                      VoteRepository voteRepository, 
                      CitizenRepository citizenRepository, 
                      VotingTableRepository votingTableRepository) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.voteRepository = voteRepository;
        this.citizenRepository = citizenRepository;
        this.votingTableRepository = votingTableRepository;
        initElection();
    }

    private void initElection() {
        if (this.currentElection != null) {
            return; // Already initialized
        }
        this.currentElection = electionRepository.findCurrentElection()
            .orElseThrow(() -> new RuntimeException("No current election found"));
        this.candidates = candidateRepository.findCandidatesByElectionId(this.currentElection.getId());
        this.votingTablesByStation = votingTableRepository.groupVotingTablesByStation();
        this.citizensByTable = citizenRepository.groupCitizensByVotingTable();
    }

    @Override
    public ElectionData getElectionData(int controlCenterId, Current current) {
        if (currentElection == null) {
            initElection();
        }
        return convertElectionToElectionData(currentElection);
    }

    @Override
    public VotingTableData[] getVotingTablesFromStation(int controlCenterId, Current current) {
        if (currentElection == null) {
            initElection();
        }
        
        List<VotingTable> votingTables = votingTablesByStation.get(controlCenterId);
        if (votingTables == null) {
            return new VotingTableData[0];
        }
        return votingTables.stream()
            .map(this::convertVotingTableToVotingTableData)
            .toArray(VotingTableData[]::new);
    }

    @Override
    public void registerVote(VoteData vote, Current current) {
        if (this.candidates == null || this.votingTablesByStation == null || this.citizensByTable == null ) {
            initElection();
        }

        if (voteRepository.hasVoted(vote.citizenId)) {
            throw new RuntimeException("Citizen has already voted");
        }

        Citizen citizen = citizenRepository.findById(vote.citizenId)
            .orElseThrow(() -> new RuntimeException("Citizen not found"));
        if (citizen.getVotingTable().getId() != vote.tableId) {
            throw new RuntimeException("Citizen does not belong to this voting table");
        }
        
        Candidate candidate = this.candidates.stream()
            .filter(c -> c.getId() == vote.candidateId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Candidate not found"));

        VotingTable votingTable = votingTablesByStation.values().stream()
            .flatMap(List::stream)
            .filter(table -> table.getId() == vote.tableId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Voting table not found in server context"));

        Vote newVoteToSave = new Vote();
        newVoteToSave.setCitizenId(vote.citizenId);
        newVoteToSave.setCandidate(candidate);
        newVoteToSave.setTableId(votingTable.getId());
        newVoteToSave.setTimestamp(java.time.LocalDateTime.now());
        if (this.currentElection != null) {
            newVoteToSave.setElection(this.currentElection);
        }

        voteRepository.save(newVoteToSave);
        System.out.println("Vote registered for citizen: " + vote.citizenId);

        Map<String, String> details = new HashMap<>();
        details.put("citizenId", String.valueOf(vote.citizenId));
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
                observer._notify(event);
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
        List<Citizen> citizensInTable = citizensByTable.get(votingTable.getId());
        if (citizensInTable == null) {
            citizensInTable = new java.util.ArrayList<>();
        }
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
}