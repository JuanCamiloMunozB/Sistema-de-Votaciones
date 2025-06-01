import ElectionSystem.*;
import ElectionSystem.CandidateNotFound;
import ElectionSystem.CitizenAlreadyVoted;
import ElectionSystem.CitizenNotBelongToTable;
import ElectionSystem.CitizenNotFound;
import com.zeroc.Ice.Current;
import java.util.concurrent.atomic.AtomicBoolean; // Para el estado de la elección

// VotingTableImpl ahora implementa la interfaz combinada
public class VotingTableImpl implements VotingTableCombinedService {

    private ControlCenterServicePrx controlCenterService;
    private String votingTableId; // Necesitaremos un ID para la suscripción
    private AtomicBoolean electionActive = new AtomicBoolean(false); // Estado de la elección

    // Constructor modificado para incluir votingTableId
    public VotingTableImpl(ControlCenterServicePrx controlCenterService, String votingTableId) {
        this.controlCenterService = controlCenterService;
        this.votingTableId = votingTableId;
    }

    // Implementación de ElectionActivityObserver
    @Override
    public void electionStarted(Current current) {
        electionActive.set(true);
        System.out.println("VotingTable [" + votingTableId + "]: Election has started. Accepting votes.");
    }

    @Override
    public void electionEnded(Current current) {
        electionActive.set(false);
        System.out.println("VotingTable [" + votingTableId + "]: Election has ended. Not accepting votes.");
    }
    
    @Override
    public void emitVote(VoteData vote, Current current) throws ElectionInactive, CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable {
        if (!electionActive.get()) {
            System.out.println("VotingTable [" + votingTableId + "]: Election is not active. Vote rejected.");
            throw new ElectionInactive("Election is not currently active.");
        }
        try {
            System.out.println("VotingTable [" + votingTableId + "]: Attempting to emit vote for citizen: " + vote.citizenDocument);
            controlCenterService.submitVote(vote);
            System.out.println("VotingTable [" + votingTableId + "]: Vote emitted successfully for citizen: " + vote.citizenDocument);
        } catch (CitizenAlreadyVoted | CitizenNotFound | CandidateNotFound | CitizenNotBelongToTable e) {
            System.err.println("VotingTable [" + votingTableId + "]: Error emitting vote: " + e.ice_name() + " - " + e.getMessage());
            throw e;
        } catch (com.zeroc.Ice.CommunicatorDestroyedException | com.zeroc.Ice.ObjectNotExistException e) {
            System.err.println("VotingTable [" + votingTableId + "]: Control Center seems to be down. Could not emit vote. " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("VotingTable [" + votingTableId + "]: An unexpected error occurred while emitting vote to Control Center: " + e.getMessage());
            throw new com.zeroc.Ice.UnknownException(e);
        }
    }

    public String getVotingTableId() {
        return votingTableId;
    }

    public boolean isElectionActive() {
        return electionActive.get();
    }
}