import ElectionSystem.*;

import com.zeroc.Ice.Current;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class VotingTableImpl implements VotingTableCombinedService {

    private ControlCenterServicePrx controlCenterService;
    private String votingTableId;
    private AtomicBoolean electionActive = new AtomicBoolean(false);

    public VotingTableImpl(ControlCenterServicePrx controlCenterService, String votingTableId) {
        this.controlCenterService = controlCenterService;
        this.votingTableId = votingTableId;
    }

    @Override
    public void electionStarted(Current current) {
        electionActive.set(true);
        System.out.println("VotingTable [" + votingTableId + "]: Election started.");
    }

    @Override
    public void electionEnded(Current current) {
        electionActive.set(false);
        System.out.println("VotingTable [" + votingTableId + "]: Election ended.");
    }
    
    @Override
    public void emitVote(VoteData vote, Current current) throws ElectionInactive, CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable {
        if (!electionActive.get()) {
            throw new ElectionInactive("Election is not currently active.");
        }
        try {
            controlCenterService.submitVote(vote);
            System.out.println("VotingTable [" + votingTableId + "]: Vote submitted for citizen: " + vote.citizenDocument);
        } catch (CitizenAlreadyVoted | CitizenNotFound | CandidateNotFound | CitizenNotBelongToTable e) {
            System.err.println("VotingTable [" + votingTableId + "]: Vote rejected: " + e.ice_name());
            throw e;
        } catch (com.zeroc.Ice.CommunicatorDestroyedException | com.zeroc.Ice.ObjectNotExistException e) {
            System.err.println("VotingTable [" + votingTableId + "]: Control Center unavailable.");
            throw e;
        } catch (Exception e) {
            System.err.println("VotingTable [" + votingTableId + "]: Unexpected error: " + e.getMessage());
            throw new com.zeroc.Ice.UnknownException(e);
        }
    }

    public String getVotingTableId() {
        return votingTableId;
    }

    public boolean isElectionActive() {
        return electionActive.get();
    }

    @Override
    public int vote(String document, int candidateId, Current current) {
        try {
            if (!electionActive.get()) {
                return 0;
            }

            int numericTableId;
            try {
                String numericPart = votingTableId.replaceAll("[^0-9]", "");
                numericTableId = numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
            } catch (NumberFormatException e) {
                numericTableId = 0;
            }

            VoteData vote = new VoteData(
                document, 
                candidateId, 
                numericTableId, 
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            );

            controlCenterService.submitVote(vote);
            return 0;
            
        } catch (CitizenNotFound e) {
            return 3;
        } catch (CitizenNotBelongToTable e) {
            return 1;
        } catch (CitizenAlreadyVoted e) {
            return 2;
        } catch (CandidateNotFound e) {
            return 0;
        } catch (ElectionInactive e) {
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}