import ElectionSystem.*;
import com.zeroc.Ice.Current;
import models.elections.Candidate;
import models.elections.Election;
import models.elections.Vote;
import models.votaciones.Citizen;
import models.votaciones.VotingTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repositories.elections.CandidateRepository;
import repositories.elections.ElectionRepository;
import repositories.elections.VoteRepository;
import repositories.votaciones.CitizenRepository;
import repositories.votaciones.VotingTableRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerImplTest {

    @Mock
    private ElectionRepository electionRepository;
    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private CitizenRepository citizenRepository;
    @Mock
    private VotingTableRepository votingTableRepository;
    @Mock
    private Current current;

    private ServerImpl serverImpl;

    private Election currentElection;
    private List<Candidate> candidates;
    private Map<Integer, List<VotingTable>> votingTablesByStationMapSetup;
    private Map<Integer, List<Citizen>> citizensByTableMapSetup;

    @BeforeEach
    void setUp() {
        currentElection = new Election();
        currentElection.setId(1);
        currentElection.setName("Test Election");
        currentElection.setStartTime(LocalDateTime.now().minusDays(1));
        currentElection.setEndTime(LocalDateTime.now().plusDays(1));

        candidates = new ArrayList<>();
        Candidate candidate1 = new Candidate();
        candidate1.setId(101);
        candidate1.setFirstName("CandidateA");
        candidate1.setLastName("Test");
        candidate1.setParty("PartyX");
        candidate1.setElection(currentElection);
        candidates.add(candidate1);

        VotingTable table201 = new VotingTable();
        table201.setId(201);

        votingTablesByStationMapSetup = new HashMap<>();
        List<VotingTable> tablesForStationKey1 = new ArrayList<>();
        tablesForStationKey1.add(table201);
        votingTablesByStationMapSetup.put(1, tablesForStationKey1);

        Citizen citizen1 = new Citizen();
        citizen1.setId(1);
        citizen1.setVotingTable(table201);

        citizensByTableMapSetup = new HashMap<>();
        List<Citizen> citizensForTable201 = new ArrayList<>();
        citizensForTable201.add(citizen1);
        citizensByTableMapSetup.put(201, citizensForTable201);

        when(electionRepository.findCurrentElection()).thenReturn(Optional.of(currentElection));
        when(candidateRepository.findCandidatesByElectionId(currentElection.getId())).thenReturn(candidates);
        when(votingTableRepository.groupVotingTablesByStation()).thenReturn(votingTablesByStationMapSetup);
        when(citizenRepository.groupCitizensByVotingTable()).thenReturn(citizensByTableMapSetup);

        serverImpl = new ServerImpl(electionRepository, candidateRepository, voteRepository, citizenRepository, votingTableRepository);
    }

    @Test
    void registerVote_Successful() {
        VoteData voteData = new VoteData(1, 101, 201, "ts");

        Citizen citizenFromRepo = new Citizen();
        citizenFromRepo.setId(1);
        VotingTable citizenVotingTable = new VotingTable();
        citizenVotingTable.setId(201);
        citizenFromRepo.setVotingTable(citizenVotingTable);
        
        when(voteRepository.hasVoted(voteData.citizenId)).thenReturn(false);
        when(citizenRepository.findById(voteData.citizenId)).thenReturn(Optional.of(citizenFromRepo));

        assertDoesNotThrow(() -> serverImpl.registerVote(voteData, current), 
            "El voto debería registrarse sin excepción. Verificar que la mesa " + voteData.tableId + " existe en el contexto del servidor.");
        
        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    void registerVote_CitizenAlreadyVoted_ShouldThrowException() {
        VoteData voteData = new VoteData(1, 101, 201, "ts");
        when(voteRepository.hasVoted(voteData.citizenId)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Citizen has already voted", exception.getMessage());
        verify(voteRepository, never()).save(any());
    }

    @Test
    void registerVote_CitizenNotFound_ShouldThrowException() {
        VoteData voteData = new VoteData(1, 101, 201, "ts");
        when(voteRepository.hasVoted(voteData.citizenId)).thenReturn(false);
        when(citizenRepository.findById(voteData.citizenId)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Citizen not found", exception.getMessage());
        verify(voteRepository, never()).save(any());
    }

    @Test
    void registerVote_CitizenDoesNotBelongToTable_ShouldThrowException() {
        VoteData voteData = new VoteData(1, 101, 201, "ts");
        
        Citizen citizenFromRepo = new Citizen();
        citizenFromRepo.setId(1);
        VotingTable differentVotingTable = new VotingTable();
        differentVotingTable.setId(999);
        citizenFromRepo.setVotingTable(differentVotingTable);

        when(voteRepository.hasVoted(voteData.citizenId)).thenReturn(false);
        when(citizenRepository.findById(voteData.citizenId)).thenReturn(Optional.of(citizenFromRepo));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Citizen does not belong to this voting table", exception.getMessage());
        verify(voteRepository, never()).save(any());
    }

    @Test
    void registerVote_CandidateNotFound_ShouldThrowException() {
        VoteData voteData = new VoteData(1, 999, 201, "ts");
        
        Citizen citizenFromRepo = new Citizen();
        citizenFromRepo.setId(1);
        VotingTable citizenVotingTable = new VotingTable();
        citizenVotingTable.setId(201);
        citizenFromRepo.setVotingTable(citizenVotingTable);

        when(voteRepository.hasVoted(voteData.citizenId)).thenReturn(false);
        when(citizenRepository.findById(voteData.citizenId)).thenReturn(Optional.of(citizenFromRepo));
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Candidate not found", exception.getMessage());
        verify(voteRepository, never()).save(any());
    }
} 