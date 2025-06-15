import ElectionSystem.ServerServicePrx;
import ElectionSystem.VoteData;
import ElectionSystem.CitizenNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import ElectionSystem.ElectionInactive;
import com.zeroc.Ice.UnknownException;

@ExtendWith(MockitoExtension.class)
class ControlCenterImplTest {

    @Mock
    private ServerServicePrx serverServicePrx;

    @Mock
    private com.zeroc.Ice.Current current;

    @Spy
    @InjectMocks
    private ControlCenterImpl controlCenterImpl;

    private ServerServicePrx mockServerService;

    @BeforeEach
    void setUp() {
        controlCenterImpl.pendingVotes.clear();
        
        // Create mock for ServerService
        mockServerService = mock(ServerServicePrx.class);
        
        // Inject the mock into controlCenterImpl (you may need to adjust this based on your actual implementation)
        // This assumes there's a way to set the serverService in ControlCenterImpl
        try {
            // Use reflection or a setter method to inject the mock
            java.lang.reflect.Field serverServiceField = ControlCenterImpl.class.getDeclaredField("serverService");
            serverServiceField.setAccessible(true);
            serverServiceField.set(controlCenterImpl, mockServerService);
        } catch (Exception e) {
            // Handle reflection error
            e.printStackTrace();
        }
        
        // Start election to make it active for tests
        try {
            controlCenterImpl.startElection(current);
        } catch (Exception e) {
            // Election might already be started, ignore
        }
    }

    @Test
    void submitVote_serverAvailable_sendsVoteDirectly() throws Exception {
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Should not throw exception and vote should be sent to server
        assertDoesNotThrow(() -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was called
        verify(mockServerService).registerVote(vote);
    }

    @Test
    void submitVote_serverUnavailable_throwsException() throws Exception {
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Mock server to throw generic exception (server unavailable)
        doThrow(new RuntimeException("Server unavailable")).when(mockServerService).registerVote(vote);
        
        // Generic server errors should throw UnknownException
        assertThrows(UnknownException.class, () -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was attempted to be called
        verify(mockServerService).registerVote(vote);
    }

    @Test
    void submitVote_queueFull_handledAsSuccessfulProcessing() throws Exception {
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Mock server to throw queue full exception (capacity issue)
        doThrow(new RuntimeException("Vote processing queue is full. Server at maximum capacity")).when(mockServerService).registerVote(vote);
        
        // Queue full errors should be handled gracefully (not throw exception)
        assertDoesNotThrow(() -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was called
        verify(mockServerService).registerVote(vote);
    }

    @Test
    void submitVote_pendingQueueError_handledAsSuccessfulProcessing() throws Exception {
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Mock server to throw pending queue error (capacity issue)
        doThrow(new RuntimeException("pending queue")).when(mockServerService).registerVote(vote);
        
        // Pending queue errors should be handled gracefully (not throw exception)
        assertDoesNotThrow(() -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was called
        verify(mockServerService).registerVote(vote);
    }

    @Test
    void submitVote_maximumCapacityError_handledAsSuccessfulProcessing() throws Exception {
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Mock server to throw maximum capacity error
        doThrow(new RuntimeException("maximum capacity")).when(mockServerService).registerVote(vote);
        
        // Maximum capacity errors should be handled gracefully (not throw exception)
        assertDoesNotThrow(() -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was called
        verify(mockServerService).registerVote(vote);
    }

    @Test
    void submitVote_knownElectionExceptions_propagated() throws Exception {
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Mock server to throw known election system exceptions - these should still be propagated
        doThrow(new CitizenNotFound("Citizen not found")).when(mockServerService).registerVote(vote);
        
        // Should throw the same exception (not handled as capacity issue)
        assertThrows(CitizenNotFound.class, () -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was called
        verify(mockServerService).registerVote(vote);
    }

    @Test
    void startElection_setsElectionActive() throws Exception {
        // Reset election state
        controlCenterImpl.endElection(current);
        
        // Start election
        assertDoesNotThrow(() -> controlCenterImpl.startElection(current));
        
        // Verify election is active by trying to submit a vote
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        assertDoesNotThrow(() -> controlCenterImpl.submitVote(vote, current));
    }

    @Test
    void endElection_setsElectionInactive() throws Exception {
        // Ensure election is started first
        controlCenterImpl.startElection(current);
        
        // End election
        assertDoesNotThrow(() -> controlCenterImpl.endElection(current));
        
        // Verify election is inactive by trying to submit a vote
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        assertThrows(ElectionInactive.class, () -> controlCenterImpl.submitVote(vote, current));
    }

    @Test
    void submitVote_electionInactive_throwsElectionInactive() throws Exception {
        // Ensure election is ended
        controlCenterImpl.endElection(current);
        
        VoteData vote = new VoteData("12345", 1, 1, "2023-01-01 10:00:00");
        
        // Should throw ElectionInactive exception
        assertThrows(ElectionInactive.class, () -> controlCenterImpl.submitVote(vote, current));
        
        // Verify server was never called
        verify(mockServerService, never()).registerVote(any());
    }
}