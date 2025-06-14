import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import com.zeroc.Ice.Properties;

import utils.JPAUtil;
import repositories.elections.*;
import repositories.votaciones.*;
import repositories.elections.VotedCitizenRepository;

public class ServerMain {
    
    public static void main(String[] args) {
        Communicator communicator = null;
        try {
            communicator = Util.initialize(args);
            
            // Obtener el ID de la instancia del servidor desde las propiedades
            Properties props = communicator.getProperties();
            String instanceId = props.getProperty("Server.Instance.Id");
            
            System.out.println("ServerMain (" + instanceId + "): Initializing JPAUtil...");
            JPAUtil.initialize(communicator);
            System.out.println("ServerMain (" + instanceId + "): JPAUtil initialized.");

            System.out.println("ServerMain (" + instanceId + "): Creating ObjectAdapter 'ServerAdapter'...");
            ObjectAdapter adapter = communicator.createObjectAdapter("ServerAdapter");
            System.out.println("ServerMain (" + instanceId + "): ObjectAdapter created.");

            // Crear repositorios
            ElectionRepository electionRepository = new ElectionRepository();
            CandidateRepository candidateRepository = new CandidateRepository();
            VoteRepository voteRepository = new VoteRepository();
            CitizenRepository citizenRepository = new CitizenRepository();
            VotingTableRepository votingTableRepository = new VotingTableRepository();
            VotedCitizenRepository votedCitizenRepository = new VotedCitizenRepository();

            // Determinar la identidad basada en la instancia
            String identity = "ServerService";
            if ("ServerInstance1".equals(instanceId)) {
                identity = "ServerService1";
            } else if ("ServerInstance2".equals(instanceId)) {
                identity = "ServerService2";
            }

            System.out.println("ServerMain (" + instanceId + "): Adding ServerImpl to adapter with identity '" + identity + "'...");
            adapter.add(new ServerImpl(electionRepository, candidateRepository, voteRepository, 
                       citizenRepository, votingTableRepository, votedCitizenRepository), 
                       Util.stringToIdentity(identity));
            System.out.println("ServerMain (" + instanceId + "): ServerImpl added to adapter.");

            System.out.println("ServerMain (" + instanceId + "): Activating adapter...");
            adapter.activate();
            System.out.println("ServerMain (" + instanceId + "): Adapter activated.");
            System.out.println("ServerService (" + instanceId + ") ready and registered.");
            
            communicator.waitForShutdown();
        } catch (Throwable t) {
            System.err.println("ServerMain: CRITICAL ERROR during startup or execution: " + t.getMessage());
            t.printStackTrace();
        } finally {
            if (communicator != null) {
                JPAUtil.shutdown(); 
            }
        }
    }
}