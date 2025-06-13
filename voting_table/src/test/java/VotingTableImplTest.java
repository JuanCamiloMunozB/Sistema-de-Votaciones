import ElectionSystem.ControlCenterServicePrx;
import ElectionSystem.VoteData;
import ElectionSystem.ElectionInactive;
import ElectionSystem.CitizenAlreadyVoted;
import ElectionSystem.CitizenNotFound;
import ElectionSystem.CitizenNotBelongToTable;
import ElectionSystem.CandidateNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VotingTableImplTest {

    @Mock
    private ControlCenterServicePrx controlCenterServicePrx;

    @Mock
    private com.zeroc.Ice.Current current; // Mock de Current

    private VotingTableImpl votingTableImpl;
    private String testTableId = "TestTable1"; // ID para la mesa de prueba
    private final String SAMPLE_CITIZEN_DOCUMENT_VT = "DOC_TEST_VT"; // Documento para VoteData
    private final int SAMPLE_CANDIDATE_ID = 101;

    private VoteData sampleVoteData;

    @BeforeEach
    void setUp() {
        sampleVoteData = new VoteData(SAMPLE_CITIZEN_DOCUMENT_VT, 101, 201, "test-ts-vt");
        votingTableImpl = new VotingTableImpl(controlCenterServicePrx, testTableId);
        votingTableImpl.electionStarted(null); 
    }

    @Test
    void emitVote_success_callsControlCenterSubmitVote() throws Exception {
        assertDoesNotThrow(() -> votingTableImpl.emitVote(sampleVoteData, current));
        verify(controlCenterServicePrx, times(1)).submitVote(sampleVoteData);
    }

    @Test
    void emitVote_controlCenterThrowsException_rethrowsException() throws Exception {
        CitizenAlreadyVoted expectedException = new CitizenAlreadyVoted("Citizen already voted via Control Center");
        doThrow(expectedException).when(controlCenterServicePrx).submitVote(sampleVoteData);

        CitizenAlreadyVoted actualException = assertThrows(CitizenAlreadyVoted.class, () -> {
            votingTableImpl.emitVote(sampleVoteData, current);
        });

        assertSame(expectedException, actualException, "Should rethrow the specific exception from Control Center");
        verify(controlCenterServicePrx, times(1)).submitVote(sampleVoteData);
    }

    @Test
    void emitVote_electionNotActive_throwsElectionInactive() throws Exception {
        VotingTableImpl tableForInactiveTest = new VotingTableImpl(controlCenterServicePrx, testTableId + "_inactive");
        assertThrows(ElectionInactive.class, () -> {
            tableForInactiveTest.emitVote(sampleVoteData, current);
        });
        verify(controlCenterServicePrx, never()).submitVote(any(VoteData.class));
    }

    @Test
    void vote_success_returns0() throws Exception {
        // Arrange: No exception thrown means successful vote
        doNothing().when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(0, result, "Should return 0 for successful vote");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_citizenNotBelongToTable_returns1() throws Exception {
        // Arrange
        doThrow(new CitizenNotBelongToTable("Citizen does not belong to this table"))
            .when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(1, result, "Should return 1 when citizen doesn't belong to this table");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_citizenAlreadyVoted_returns2() throws Exception {
        // Arrange
        doThrow(new CitizenAlreadyVoted("Citizen has already voted"))
            .when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(2, result, "Should return 2 when citizen already voted");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_citizenNotFound_returns3() throws Exception {
        // Arrange
        doThrow(new CitizenNotFound("Citizen not found in database"))
            .when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(3, result, "Should return 3 when citizen is not found in database");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_candidateNotFound_returns0() throws Exception {
        // Arrange
        doThrow(new CandidateNotFound("Candidate not found"))
            .when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(0, result, "Should return 0 when candidate not found (citizen can vote, but candidate issue)");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_electionInactive_returns0() throws Exception {
        // Arrange
        doThrow(new ElectionInactive("Election is not active"))
            .when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(0, result, "Should return 0 when election is inactive from ControlCenter");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_electionNotActiveLocally_returns0() throws Exception {
        // Arrange: Create a table with inactive election
        VotingTableImpl inactiveTable = new VotingTableImpl(controlCenterServicePrx, "InactiveTable1");
        // Don't call electionStarted() so it remains inactive
        
        // Act
        int result = inactiveTable.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(0, result, "Should return 0 when election is not active locally");
        verify(controlCenterServicePrx, never()).submitVote(any(VoteData.class));
    }

    @Test
    void vote_unexpectedException_returns0() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Unexpected error"))
            .when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        int result = votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert
        assertEquals(0, result, "Should return 0 for unexpected exceptions");
        verify(controlCenterServicePrx, times(1)).submitVote(any(VoteData.class));
    }

    @Test
    void vote_correctVoteDataCreation() throws Exception {
        // Arrange
        doNothing().when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        votingTableImpl.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert: Verify the VoteData was created correctly
        verify(controlCenterServicePrx, times(1)).submitVote(argThat(voteData -> 
            voteData.citizenDocument.equals(SAMPLE_CITIZEN_DOCUMENT_VT) &&
            voteData.candidateId == SAMPLE_CANDIDATE_ID &&
            voteData.tableId == 1 && // TestTable1 -> numeric part is 1
            voteData.timestamp != null && !voteData.timestamp.isEmpty()
        ));
    }

    @Test
    void vote_tableIdWithoutNumbers_usesZero() throws Exception {
        // Arrange
        VotingTableImpl tableWithoutNumbers = new VotingTableImpl(controlCenterServicePrx, "TableABC");
        tableWithoutNumbers.electionStarted(null);
        doNothing().when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        tableWithoutNumbers.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert: Verify tableId is 0 when no numbers in table ID
        verify(controlCenterServicePrx, times(1)).submitVote(argThat(voteData -> 
            voteData.tableId == 0
        ));
    }

    @Test
    void vote_multipleNumbers_extractsCorrectly() throws Exception {
        // Arrange
        VotingTableImpl tableMultipleNumbers = new VotingTableImpl(controlCenterServicePrx, "Table123Test456");
        tableMultipleNumbers.electionStarted(null);
        doNothing().when(controlCenterServicePrx).submitVote(any(VoteData.class));
        
        // Act
        tableMultipleNumbers.vote(SAMPLE_CITIZEN_DOCUMENT_VT, SAMPLE_CANDIDATE_ID, current);
        
        // Assert: Verify tableId extracts all numbers correctly
        verify(controlCenterServicePrx, times(1)).submitVote(argThat(voteData -> 
            voteData.tableId == 123456 // Should concatenate all numbers
        ));
    }
}