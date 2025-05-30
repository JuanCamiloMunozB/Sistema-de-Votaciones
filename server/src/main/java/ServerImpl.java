import ElectionSystem.*;
import models.elections.*;
import models.votaciones.Citizen;
import models.votaciones.VotingTable;
import repositories.elections.*;
import repositories.votaciones.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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

        Vote newVote = new Vote();
        newVote.setCitizenId(vote.citizenId);
        newVote.setCandidate(candidate);
        newVote.setTableId(votingTable.getId());
        newVote.setTimestamp(java.time.LocalDateTime.now());

        voteRepository.save(newVote);
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

    private CandidateData convertCandidateToCandidateData(Candidate candidate) {
        return new CandidateData(
            candidate.getId(),
            candidate.getFirstName(),
            candidate.getLastName(),
            candidate.getParty()
        );
    }
}