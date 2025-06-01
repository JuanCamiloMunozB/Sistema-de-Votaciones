import ElectionSystem.ServerServicePrx;
import ElectionSystem.VoteData;
import ElectionSystem.CitizenAlreadyVoted;
import ElectionSystem.CitizenNotFound;
import com.zeroc.Ice.ConnectTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlCenterImplTest {

    @Mock
    private ServerServicePrx serverServicePrx;

    @Mock
    private com.zeroc.Ice.Current current; // Mock de Current

    @Spy // Para acceder a la cola pendingVotes o para espiar métodos si es necesario
    @InjectMocks
    private ControlCenterImpl controlCenterImpl;

    private VoteData sampleVoteData;

    @BeforeEach
    void setUp() {
        sampleVoteData = new VoteData("DOC001", 101, 201, "test-ts");
        controlCenterImpl.pendingVotes.clear();
    }

    @Test
    void submitVote_serverAvailable_sendsVoteDirectly() throws Exception {
        assertDoesNotThrow(() -> controlCenterImpl.submitVote(sampleVoteData, current));

        verify(serverServicePrx, times(1)).registerVote(sampleVoteData);
        assertTrue(controlCenterImpl.pendingVotes.isEmpty(), "Pending votes queue should be empty");
    }

    @Test
    void submitVote_serverUnavailable_addsToPendingQueue() throws Exception {
        doThrow(new ConnectTimeoutException()).when(serverServicePrx).registerVote(sampleVoteData);

        controlCenterImpl.submitVote(sampleVoteData, current);

        verify(serverServicePrx, times(1)).registerVote(sampleVoteData);
        assertEquals(1, controlCenterImpl.pendingVotes.size(), "Vote should be added to pending queue");
        assertSame(sampleVoteData, controlCenterImpl.pendingVotes.peek(), "Correct vote data should be in queue");
    }

    @Test
    void submitVote_serverError_throwsExceptionAndNotQueued() throws Exception {
        CitizenAlreadyVoted serverSideError = new CitizenAlreadyVoted("Citizen DOC001 has already voted");
        doThrow(serverSideError).when(serverServicePrx).registerVote(sampleVoteData);

        CitizenAlreadyVoted thrown = assertThrows(CitizenAlreadyVoted.class, () -> {
            controlCenterImpl.submitVote(sampleVoteData, current);
        });

        assertSame(serverSideError, thrown, "Should rethrow the specific server side exception");
        verify(serverServicePrx, times(1)).registerVote(sampleVoteData);
        assertTrue(controlCenterImpl.pendingVotes.isEmpty(), "Vote should not be added to queue on server error");
    }

    @Test
    void processPendingVotes_sendsQueuedVote_whenServerBecomesAvailable() throws Exception {
        controlCenterImpl.pendingVotes.add(sampleVoteData);
        VoteData anotherVote = new VoteData("DOC002", 102, 202, "ts2");
        controlCenterImpl.pendingVotes.add(anotherVote);

        doNothing().when(serverServicePrx).registerVote(sampleVoteData);
        doThrow(new ConnectTimeoutException()).when(serverServicePrx).registerVote(anotherVote);

        controlCenterImpl.processPendingVotes();

        verify(serverServicePrx, times(1)).registerVote(sampleVoteData);
        verify(serverServicePrx, times(1)).registerVote(anotherVote);
        assertEquals(1, controlCenterImpl.pendingVotes.size(), "One vote should remain in queue");
        assertSame(anotherVote, controlCenterImpl.pendingVotes.peek(), "The second vote should be the one remaining");
    }

    @Test
    void processPendingVotes_removesVote_onNonRecoverableError() throws Exception {
        controlCenterImpl.pendingVotes.add(sampleVoteData);
        CitizenNotFound nonRecoverableError = new CitizenNotFound("Citizen for vote data not found during processing");
        doThrow(nonRecoverableError).when(serverServicePrx).registerVote(sampleVoteData);

        controlCenterImpl.processPendingVotes();

        verify(serverServicePrx, times(1)).registerVote(sampleVoteData);
        assertTrue(controlCenterImpl.pendingVotes.isEmpty(), "Vote should be removed from queue on non-recoverable error");
    }

     @Test
    void processPendingVotes_emptyQueue_doesNothing() throws Exception {
        assertTrue(controlCenterImpl.pendingVotes.isEmpty());
        
        controlCenterImpl.processPendingVotes();
        
        verify(serverServicePrx, never()).registerVote(any(VoteData.class));
        assertTrue(controlCenterImpl.pendingVotes.isEmpty());
    }
}