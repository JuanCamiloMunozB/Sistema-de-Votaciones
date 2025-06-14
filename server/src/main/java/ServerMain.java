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
            
            if (instanceId == null || instanceId.isEmpty()) {
                throw new RuntimeException("Server.Instance.Id property not found in configuration");
            }
            
            System.out.println("ServerMain (" + instanceId + "): Initializing JPAUtil...");
            JPAUtil.initialize(communicator);
            System.out.println("ServerMain (" + instanceId + "): JPAUtil initialized.");

            // Determinar el nombre del adaptador basado en la instancia
            String adapterName = "ServerAdapter1";
            if ("ServerInstance2".equals(instanceId)) {
                adapterName = "ServerAdapter2";
            }
            
            System.out.println("ServerMain (" + instanceId + "): Creating ObjectAdapter '" + adapterName + "'...");
            ObjectAdapter adapter = communicator.createObjectAdapter(adapterName);
            System.out.println("ServerMain (" + instanceId + "): ObjectAdapter created.");
            
            // Log adapter information
            System.out.println("ServerMain (" + instanceId + "): Adapter endpoints: " + adapter.getEndpoints().length + " endpoints");
            for (int i = 0; i < adapter.getEndpoints().length; i++) {
                System.out.println("  Endpoint " + i + ": " + adapter.getEndpoints()[i].toString());
            }

            // Crear repositorios
            ElectionRepository electionRepository = new ElectionRepository();
            CandidateRepository candidateRepository = new CandidateRepository();
            VoteRepository voteRepository = new VoteRepository();
            CitizenRepository citizenRepository = new CitizenRepository();
            VotingTableRepository votingTableRepository = new VotingTableRepository();
            VotedCitizenRepository votedCitizenRepository = new VotedCitizenRepository();

            // Use the common identity for both servers
            String identity = "ServerService";
            
            System.out.println("ServerMain (" + instanceId + "): Adding ServerImpl to adapter with identity '" + identity + "'...");
            adapter.add(new ServerImpl(electionRepository, candidateRepository, voteRepository, 
                       citizenRepository, votingTableRepository, votedCitizenRepository), 
                       Util.stringToIdentity(identity));
            System.out.println("ServerMain (" + instanceId + "): ServerImpl added to adapter.");

            System.out.println("ServerMain (" + instanceId + "): Activating adapter...");
            adapter.activate();
            System.out.println("ServerMain (" + instanceId + "): Adapter activated.");
            
            // Log final adapter state
            System.out.println("ServerMain (" + instanceId + "): Final adapter endpoints: " + adapter.getEndpoints().length + " endpoints");
            for (int i = 0; i < adapter.getEndpoints().length; i++) {
                System.out.println("  Final Endpoint " + i + ": " + adapter.getEndpoints()[i].toString());
            }
            
            System.out.println("ServerMain (" + instanceId + "): Server activation completed successfully.");
            System.out.println("ServerService (" + instanceId + ") ready and registered.");
            
            // Add a small delay to ensure everything is properly initialized
            Thread.sleep(2000); // Increased delay
            
            communicator.waitForShutdown();
        } catch (Throwable t) {
            System.err.println("ServerMain: CRITICAL ERROR during startup or execution: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        } finally {
            if (communicator != null) {
                JPAUtil.shutdown();
                communicator.destroy();
            }
        }
    }
}