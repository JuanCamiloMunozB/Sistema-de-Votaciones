import ElectionSystem.ControlCenterServicePrx;
import ElectionSystem.VoteData;
import ElectionSystem.ElectionInactive;
import ElectionSystem.CitizenAlreadyVoted;
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
}