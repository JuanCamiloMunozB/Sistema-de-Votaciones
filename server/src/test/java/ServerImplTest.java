import ElectionSystem.*;
import com.zeroc.Ice.Current;
import models.elections.*;
import models.elections.VotedCitizen;
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
import repositories.elections.VotedCitizenRepository;
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
    private VotedCitizenRepository votedCitizenRepository;
    @Mock
    private Current current;

    private ServerImpl serverImpl;

    private Election currentElection;
    private List<Candidate> candidates;
    private Map<Integer, List<VotingTable>> votingTablesByStationMapSetup;

    private Citizen sampleCitizen;
    private VotingTable sampleVotingTable;
    private final String SAMPLE_CITIZEN_DOCUMENT = "DOC001";
    private final int SAMPLE_CITIZEN_ID = 1;
    private final int SAMPLE_TABLE_ID = 201;
    private final int SAMPLE_CANDIDATE_ID = 101;

    @BeforeEach
    void setUp() throws Exception {
        currentElection = new Election();
        currentElection.setId(1);
        currentElection.setName("Test Election");
        currentElection.setStartTime(LocalDateTime.now().minusDays(1));
        currentElection.setEndTime(LocalDateTime.now().plusDays(1));

        candidates = new ArrayList<>();
        Candidate candidate1 = new Candidate();
        candidate1.setId(SAMPLE_CANDIDATE_ID);
        candidate1.setFirstName("CandidateA");
        candidate1.setLastName("Test");
        candidate1.setParty("PartyX");
        candidate1.setElection(currentElection);
        candidates.add(candidate1);

        sampleVotingTable = new VotingTable();
        sampleVotingTable.setId(SAMPLE_TABLE_ID);

        votingTablesByStationMapSetup = new HashMap<>();
        List<VotingTable> tablesForStationKey1 = new ArrayList<>();
        tablesForStationKey1.add(sampleVotingTable);
        votingTablesByStationMapSetup.put(1, tablesForStationKey1); // Asumiendo que la estación es 1

        sampleCitizen = new Citizen();
        sampleCitizen.setId(SAMPLE_CITIZEN_ID);
        sampleCitizen.setDocument(SAMPLE_CITIZEN_DOCUMENT);
        sampleCitizen.setVotingTable(sampleVotingTable);

        when(electionRepository.findCurrentElection()).thenReturn(Optional.of(currentElection));
        when(candidateRepository.findCandidatesByElectionId(currentElection.getId())).thenReturn(candidates);
        when(votingTableRepository.groupVotingTablesByStation()).thenReturn(votingTablesByStationMapSetup);

        serverImpl = new ServerImpl(electionRepository, candidateRepository, voteRepository, citizenRepository, votingTableRepository, votedCitizenRepository);
    }

    @Test
    void registerVote_Successful() throws Exception {
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts");
        
        when(citizenRepository.findByDocument(SAMPLE_CITIZEN_DOCUMENT)).thenReturn(Optional.of(sampleCitizen));
        when(votedCitizenRepository.existsById(SAMPLE_CITIZEN_ID)).thenReturn(false);

        assertDoesNotThrow(() -> serverImpl.registerVote(voteData, current));
        
        verify(voteRepository).save(any(Vote.class));
        verify(votedCitizenRepository).save(new VotedCitizen(SAMPLE_CITIZEN_ID));
    }

    @Test
    void registerVote_CitizenAlreadyVoted_ShouldThrowException() throws Exception {
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts");

        when(citizenRepository.findByDocument(SAMPLE_CITIZEN_DOCUMENT)).thenReturn(Optional.of(sampleCitizen));
        when(votedCitizenRepository.existsById(SAMPLE_CITIZEN_ID)).thenReturn(true);

        CitizenAlreadyVoted exception = assertThrows(CitizenAlreadyVoted.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Citizen with document " + SAMPLE_CITIZEN_DOCUMENT + " (ID: " + SAMPLE_CITIZEN_ID + ") has already voted", exception.reason);
        verify(voteRepository, never()).save(any());
        verify(votedCitizenRepository, never()).save(any(VotedCitizen.class));
    }

    @Test
    void registerVote_CitizenNotFound_ShouldThrowException() throws Exception {
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts");
        
        when(citizenRepository.findByDocument(SAMPLE_CITIZEN_DOCUMENT)).thenReturn(Optional.empty());

        CitizenNotFound exception = assertThrows(CitizenNotFound.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Citizen with document " + SAMPLE_CITIZEN_DOCUMENT + " not found", exception.reason);
        verify(voteRepository, never()).save(any());
        verify(votedCitizenRepository, never()).save(any(VotedCitizen.class));
        verify(votedCitizenRepository, never()).existsById(anyInt());
    }

    @Test
    void registerVote_CitizenDoesNotBelongToTable_ShouldThrowException() throws Exception {
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts");
        
        Citizen citizenFromWrongTable = new Citizen();
        citizenFromWrongTable.setId(SAMPLE_CITIZEN_ID);
        citizenFromWrongTable.setDocument(SAMPLE_CITIZEN_DOCUMENT);
        VotingTable differentVotingTable = new VotingTable();
        differentVotingTable.setId(999);
        citizenFromWrongTable.setVotingTable(differentVotingTable);

        when(citizenRepository.findByDocument(SAMPLE_CITIZEN_DOCUMENT)).thenReturn(Optional.of(citizenFromWrongTable));
        when(votedCitizenRepository.existsById(SAMPLE_CITIZEN_ID)).thenReturn(false);

        CitizenNotBelongToTable exception = assertThrows(CitizenNotBelongToTable.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Citizen with document " + SAMPLE_CITIZEN_DOCUMENT + " (ID: " + SAMPLE_CITIZEN_ID + ") does not belong to voting table " + SAMPLE_TABLE_ID, exception.reason);
        verify(voteRepository, never()).save(any());
        verify(votedCitizenRepository, never()).save(any(VotedCitizen.class));
    }

    @Test
    void registerVote_CandidateNotFound_ShouldThrowException() throws Exception {
        int nonExistentCandidateId = 999;
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, nonExistentCandidateId, SAMPLE_TABLE_ID, "ts");
        
        when(citizenRepository.findByDocument(SAMPLE_CITIZEN_DOCUMENT)).thenReturn(Optional.of(sampleCitizen));
        when(votedCitizenRepository.existsById(SAMPLE_CITIZEN_ID)).thenReturn(false);
        
        CandidateNotFound exception = assertThrows(CandidateNotFound.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        assertEquals("Candidate with ID " + nonExistentCandidateId + " not found", exception.reason);
        verify(voteRepository, never()).save(any());
        verify(votedCitizenRepository, never()).save(any(VotedCitizen.class));
    }
}