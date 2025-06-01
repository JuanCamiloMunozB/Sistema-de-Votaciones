import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import utils.JPAUtil;
import repositories.elections.*;
import repositories.votaciones.*;
import repositories.elections.VotedCitizenRepository;

public class ServerMain {
    
    public static void main(String[] args) {
        Communicator communicator = null;
        try {
            communicator = Util.initialize(args, "config.server.cfg");
            System.out.println("ServerMain: Initializing JPAUtil...");
            JPAUtil.initialize(communicator);
            System.out.println("ServerMain: JPAUtil initialized.");

            System.out.println("ServerMain: Creating ObjectAdapter 'ServerAdapter'...");
            ObjectAdapter adapter = communicator.createObjectAdapter("ServerAdapter");
            System.out.println("ServerMain: ObjectAdapter 'ServerAdapter' created.");

            ElectionRepository electionRepository = new ElectionRepository();
            CandidateRepository candidateRepository = new CandidateRepository();
            VoteRepository voteRepository = new VoteRepository();
            CitizenRepository citizenRepository = new CitizenRepository();
            VotingTableRepository votingTableRepository = new VotingTableRepository();
            VotedCitizenRepository votedCitizenRepository = new VotedCitizenRepository();

            System.out.println("ServerMain: Adding ServerImpl to adapter with identity 'ServerService'...");
            adapter.add(new ServerImpl(electionRepository, candidateRepository, voteRepository, citizenRepository, votingTableRepository, votedCitizenRepository), 
                        Util.stringToIdentity("ServerService"));
            System.out.println("ServerMain: ServerImpl added to adapter.");


            System.out.println("ServerMain: Activating adapter 'ServerAdapter'...");
            adapter.activate();
            System.out.println("ServerMain: Adapter 'ServerAdapter' activated.");
            System.out.println("ServerService ready and registered with adapter ServerAdapter.");
            
            communicator.waitForShutdown();
        } catch (Throwable t) {
            System.err.println("ServerMain: CRITICAL ERROR during startup or execution: " + t.getMessage());
        } finally {
            if (communicator != null) {
                 JPAUtil.shutdown(); 
            }
        }
    }
}