import ElectionSystem.*;
import com.zeroc.Ice.Current;

public class ControlCenterImpl implements ControlCenterService{

    private ServerServicePrx serverService;

    public ControlCenterImpl(ServerServicePrx serverService) {
        this.serverService = serverService;
    }

    @Override
    public VotingTableData getVotingTableData(int tableId, Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVotingTableData'");
    }

    @Override
    public CandidateData[] getCandidates(Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCandidates'");
    }

    @Override
    public void startElection(Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'startElection'");
    }

    @Override
    public void endElection(Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'endElection'");
    }

    @Override
    public void submitVote(VoteData vote, Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'submitVote'");
    }
    
}
