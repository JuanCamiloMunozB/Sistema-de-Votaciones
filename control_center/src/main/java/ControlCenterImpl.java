import ElectionSystem.*;
import com.zeroc.Ice.Current;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

public class ControlCenterImpl implements ControlCenterService{

    private ServerServicePrx serverService;
    Queue<VoteData> pendingVotes = new LinkedList<>();
    private Timer retryTimer = new Timer();
    private static final long RETRY_INTERVAL_MS = 60000;

    public ControlCenterImpl(ServerServicePrx serverService) {
        this.serverService = serverService;
        startRetryTask();
    }

    private void startRetryTask() {
        retryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                processPendingVotes();
            }
        }, RETRY_INTERVAL_MS, RETRY_INTERVAL_MS);
    }

    synchronized void processPendingVotes() {
        while (!pendingVotes.isEmpty()) {
            VoteData vote = pendingVotes.peek();
            try {
                serverService.registerVote(vote);
                pendingVotes.poll();
                System.out.println("Pending vote sent successfully: Citizen " + vote.citizenId);
            } catch (com.zeroc.Ice.ConnectTimeoutException | com.zeroc.Ice.ConnectionRefusedException e) {
                System.err.println("Failed to send pending vote, server unavailable. Will retry later: " + e.getMessage());
                // Se sale del bucle si el servidor no está disponible, para no bloquear
                break;
            } catch (Exception e) {
                System.err.println("Error sending pending vote: " + e.getMessage());
                // De momento se elimina el voto de la cola para no reintentar indefinidamente si es un error no recuperable
                pendingVotes.poll();
            }
        }
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
        try {
            serverService.registerVote(vote);
            System.out.println("Vote submitted successfully for citizen: " + vote.citizenId);
        } catch (com.zeroc.Ice.ConnectTimeoutException | com.zeroc.Ice.ConnectionRefusedException e) {
            System.err.println("Server unavailable, adding vote to pending queue: " + e.getMessage());
            synchronized (this) {
                pendingVotes.add(vote);
            }
        } catch (Exception e) {
            System.err.println("Failed to submit vote: " + e.getMessage());
            throw e;
        }
    }
    
}
