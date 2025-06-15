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
            
            // Configure Ice for high concurrency - optimized for high volume
            Properties props = communicator.getProperties();
            
            // Enhanced multi-threading in Ice for high volume
            props.setProperty("Ice.ThreadPool.Server.Size", "100");
            props.setProperty("Ice.ThreadPool.Server.SizeMax", "300");
            props.setProperty("Ice.ThreadPool.Server.SizeWarn", "250");
            
            // Optimize connection settings for high throughput
            props.setProperty("Ice.ACM.Timeout", "60");
            props.setProperty("Ice.MessageSizeMax", "131072");
            props.setProperty("Ice.Trace.Network", "0");
            props.setProperty("Ice.Trace.Protocol", "0");
            
            // Additional optimizations for high volume
            props.setProperty("Ice.ThreadPool.Client.Size", "50");
            props.setProperty("Ice.ThreadPool.Client.SizeMax", "100");
            props.setProperty("Ice.Connection.ConnectTimeout", "10000");
            
            String instanceId = props.getProperty("Server.Instance.Id");
            
            if (instanceId == null || instanceId.isEmpty()) {
                throw new RuntimeException("Server.Instance.Id property not found in configuration");
            }
            
            System.out.println("ServerMain (" + instanceId + "): Initializing JPAUtil with connection pooling...");
            JPAUtil.initialize(communicator);
            System.out.println("ServerMain (" + instanceId + "): JPAUtil initialized.");

            String adapterName = "ServerAdapter1";
            if ("ServerInstance2".equals(instanceId)) {
                adapterName = "ServerAdapter2";
            }
            
            System.out.println("ServerMain (" + instanceId + "): Creating ObjectAdapter '" + adapterName + "'...");
            ObjectAdapter adapter = communicator.createObjectAdapter(adapterName);
            
            // Configure adapter for high throughput
            adapter.add(new ServerImpl(new ElectionRepository(), new CandidateRepository(), 
                       new VoteRepository(), new CitizenRepository(), new VotingTableRepository(), 
                       new VotedCitizenRepository()), Util.stringToIdentity("ServerService"));

            System.out.println("ServerMain (" + instanceId + "): Activating adapter...");
            adapter.activate();
            
            System.out.println("ServerService (" + instanceId + ") ready with " + 
                             props.getProperty("Ice.ThreadPool.Server.Size") + " threads (optimized for high volume).");
            
            communicator.waitForShutdown();
        } catch (Throwable t) {
            System.err.println("ServerMain: CRITICAL ERROR: " + t.getMessage());
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