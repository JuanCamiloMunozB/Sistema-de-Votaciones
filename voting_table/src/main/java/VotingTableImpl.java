import ElectionSystem.*;
import com.zeroc.Ice.Current;

public class VotingTableImpl implements VotingTableService {

    private ControlCenterServicePrx controlCenterService;

    public VotingTableImpl(ControlCenterServicePrx controlCenterService) {
        this.controlCenterService = controlCenterService;
    }
    
    @Override
    public void emitVote(VoteData vote, Current current) {
        try {
            controlCenterService.submitVote(vote);
            System.out.println("Vote emitted successfully by table for citizen: " + vote.citizenId);
        } catch (Exception e) {
            System.err.println("Error emitting vote to Control Center: " + e.getMessage());
            throw e;
        }
    }
}