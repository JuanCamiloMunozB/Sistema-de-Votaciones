import ElectionSystem.ControlCenterServicePrx;
import ElectionSystem.VoteData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private VotingTableImpl votingTableImpl;

    private VoteData sampleVoteData;

    @BeforeEach
    void setUp() {
        sampleVoteData = new VoteData(1, 101, 201, "test-ts-vt");
        // votingTableImpl se inyecta con el mock de controlCenterServicePrx automáticamente
    }

    @Test
    void emitVote_success_callsControlCenterSubmitVote() {
        // No se simula ninguna excepción en controlCenterServicePrx.submitVote()
        // para probar el caso exitoso.
        assertDoesNotThrow(() -> votingTableImpl.emitVote(sampleVoteData, current));

        // Verificar que submitVote fue llamado una vez con los datos correctos
        verify(controlCenterServicePrx, times(1)).submitVote(sampleVoteData);
    }

    @Test
    void emitVote_controlCenterThrowsException_rethrowsException() {
        // Simular que el ControlCenter lanza una excepción
        RuntimeException expectedException = new RuntimeException("Control Center Error");
        doThrow(expectedException).when(controlCenterServicePrx).submitVote(sampleVoteData);

        // Verificar que la misma excepción es relanzada por emitVote
        RuntimeException actualException = assertThrows(RuntimeException.class, () -> {
            votingTableImpl.emitVote(sampleVoteData, current);
        });

        assertSame(expectedException, actualException, "Should rethrow the exception from Control Center");
        verify(controlCenterServicePrx, times(1)).submitVote(sampleVoteData);
    }
}