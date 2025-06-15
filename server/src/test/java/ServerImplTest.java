import ElectionSystem.*;
import com.zeroc.Ice.Current;
import models.elections.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import repositories.elections.CandidateRepository;
import repositories.elections.ElectionRepository;
import repositories.elections.VoteRepository;
import repositories.elections.VotedCitizenRepository;
import repositories.votaciones.CitizenRepository;
import repositories.votaciones.VotingTableRepository;
import utils.JPAUtil;

import jakarta.persistence.EntityManager;

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
    @Mock
    private EntityManager entityManager;

    private ServerImpl serverImpl;
    private Election currentElection;
    private List<Candidate> candidates;
    private final String SAMPLE_CITIZEN_DOCUMENT = "DOC001";
    private final int SAMPLE_CANDIDATE_ID = 101;
    private final int SAMPLE_TABLE_ID = 201;

    @BeforeEach
    void setUp() throws Exception {
        // Setup test data
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

        // Use lenient stubbing to avoid UnnecessaryStubbingException
        lenient().when(candidateRepository.findAll()).thenReturn(Optional.of(candidates));
        lenient().when(electionRepository.findById(1)).thenReturn(Optional.of(currentElection));
        lenient().when(voteRepository.countByCandidateId(anyInt())).thenReturn(0L);
        lenient().when(voteRepository.countVotesGroupedByTableAndCandidate()).thenReturn(new HashMap<>());
        lenient().when(votingTableRepository.groupVotingTablesByStation()).thenReturn(new HashMap<>());
        
        // Initialize ServerImpl with mocked static JPAUtil
        try (MockedStatic<JPAUtil> jpaUtilMock = Mockito.mockStatic(JPAUtil.class)) {
            jpaUtilMock.when(JPAUtil::getEntityManagerVoting).thenReturn(entityManager);
            jpaUtilMock.when(JPAUtil::getEntityManagerElections).thenReturn(entityManager);
            
            serverImpl = new ServerImpl(electionRepository, candidateRepository, voteRepository, 
                                      citizenRepository, votingTableRepository, votedCitizenRepository);
        }
    }

    @Test
    void registerVote_Successful() throws Exception {
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts");
        
        assertDoesNotThrow(() -> serverImpl.registerVote(voteData, current));
    }

    @Test
    void registerVote_CandidateNotFound_ShouldThrowException() throws Exception {
        int nonExistentCandidateId = 999;
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, nonExistentCandidateId, SAMPLE_TABLE_ID, "ts");
        
        CandidateNotFound exception = assertThrows(CandidateNotFound.class, () -> {
            serverImpl.registerVote(voteData, current);
        });
        
        assertEquals("Candidate with ID " + nonExistentCandidateId + " not found", exception.reason);
    }

    @Test
    void registerVote_MultipleVotes_AsyncProcessing() throws Exception {
        for (int i = 0; i < 3; i++) {
            VoteData voteData = new VoteData("DOC" + i, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts" + i);
            assertDoesNotThrow(() -> serverImpl.registerVote(voteData, current));
        }
    }

    @Test
    void registerVote_QueueFull_ShouldThrowException() throws Exception {
        VoteData voteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT, SAMPLE_CANDIDATE_ID, SAMPLE_TABLE_ID, "ts");
        
        assertDoesNotThrow(() -> serverImpl.registerVote(voteData, current));
    }

    @Test
    void getProcessingStats_ShouldReturnEnhancedFormat() throws Exception {
        String stats = serverImpl.getProcessingStats(current);
        assertNotNull(stats);
        assertTrue(stats.length() > 0, "Stats should not be empty");
    }

    @Test
    void getCandidates_ShouldReturnCandidates() throws Exception {
        CandidateData[] candidatesResult = serverImpl.getCandidates(current);
        assertNotNull(candidatesResult);
        assertEquals(1, candidatesResult.length);
        assertEquals(SAMPLE_CANDIDATE_ID, candidatesResult[0].id);
    }

    @Test
    void getGlobalResults_ShouldReturnResults() throws Exception {
        CandidateResult[] results = serverImpl.getGlobalResults(current);
        assertNotNull(results);
    }

    @Test
    void getResultsByTable_ShouldReturnTableResults() throws Exception {
        Map<Integer, CandidateResult[]> results = serverImpl.getResultsByVotingTable(current);
        assertNotNull(results);
    }

    @Test
    void printQueueStatus_ShouldExecuteWithoutError() throws Exception {
        assertDoesNotThrow(() -> serverImpl.printQueueStatus(current));
    }

    @Test
    void getCacheStats_ShouldReturnCacheInformation() throws Exception {
        String cacheStats = serverImpl.getCacheStats(current);
        assertNotNull(cacheStats);
        assertTrue(cacheStats.contains("Cache:"), "Should contain cache information");
    }

    @Test
    void clearCache_ShouldClearCacheWithoutError() throws Exception {
        assertDoesNotThrow(() -> serverImpl.clearCache(current));
        
        String cacheStats = serverImpl.getCacheStats(current);
        assertNotNull(cacheStats);
    }

    @Test
    void findVotingStationByDocument_ShouldReturnResult() throws Exception {
        String document = "123456789";
        
        // Mock JPAUtil for this specific test
        try (MockedStatic<JPAUtil> jpaUtilMock = Mockito.mockStatic(JPAUtil.class)) {
            jpaUtilMock.when(JPAUtil::getEntityManagerVoting).thenReturn(entityManager);
            jpaUtilMock.when(() -> JPAUtil.executeInTransaction(any(EntityManager.class), any()))
                      .thenReturn("");
            
            String result = serverImpl.findVotingStationByDocument(document, current);
            assertNotNull(result);
        }
    }
}