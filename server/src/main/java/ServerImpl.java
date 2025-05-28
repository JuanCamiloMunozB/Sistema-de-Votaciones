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
    private List<Candidate> candidates;
    private Map<Integer, List<VotingTable>> votingTablesByStation;
    private Map<Integer, List<Citizen>> citizensByTable;
    
    public ServerImpl() {
        this.electionRepository = new ElectionRepository();
        this.candidateRepository = new CandidateRepository();
        this.voteRepository = new VoteRepository();
        this.citizenRepository = new CitizenRepository();
        this.votingTableRepository = new VotingTableRepository();
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
        return votingTables.stream()
            .map(this::convertVotingTableToVotingTableData)
            .toArray(VotingTableData[]::new);

    }

    @Override
    public void registerVote(VoteData vote, Current current) {
        Candidate candidate = candidates.stream()
            .filter(c -> c.getId() == vote.candidateId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Candidate not found"));

        VotingTable votingTable = votingTablesByStation.values().stream()
            .flatMap(List::stream)
            .filter(table -> table.getId() == vote.tableId)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Voting table not found"));

        Vote newVote = new Vote();
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
            candidates.stream()
                .map(this::convertCandidateToCandidateData)
                .toList().toArray(new CandidateData[0])
        );
    }

    private VotingTableData convertVotingTableToVotingTableData(VotingTable votingTable) {
        return new VotingTableData(
            votingTable.getId(),
            citizensByTable.get(votingTable.getId()).stream()
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