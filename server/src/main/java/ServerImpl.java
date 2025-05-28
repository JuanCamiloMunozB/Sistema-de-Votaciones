import ElectionSystem.*;
import repositories.elections.*;
import repositories.votaciones.*;
import com.zeroc.Ice.Current;

public class ServerImpl implements ServerService {
    
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final CitizenRepository citizenRepository;
    private final VotingTableRepository votingTableRepository;
    
    public ServerImpl() {
        this.electionRepository = new ElectionRepository();
        this.candidateRepository = new CandidateRepository();
        this.voteRepository = new VoteRepository();
        this.citizenRepository = new CitizenRepository();
        this.votingTableRepository = new VotingTableRepository();
    }

    @Override
    public ElectionData getElectionData(String controlCenterId, Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getElectionData'");
    }
    
    @Override
    public void registerVote(VoteData vote, Current current) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerVote'");
    }
    
    
}