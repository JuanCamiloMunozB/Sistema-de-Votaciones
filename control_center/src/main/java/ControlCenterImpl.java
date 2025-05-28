import com.zeroc.Ice.Current;

import ElectionSystem.CandidateData;
import ElectionSystem.ControlCenterService;
import ElectionSystem.VoteData;
import ElectionSystem.VotingTableData;

public class ControlCenterImpl implements ControlCenterService{

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
