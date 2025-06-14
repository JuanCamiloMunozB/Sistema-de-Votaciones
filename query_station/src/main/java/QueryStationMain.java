import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;
import ElectionSystem.*;
import java.util.Scanner;

public class QueryStationMain {
    
    private static queryStationPrx queryStationProxy;
    
    public static void main(String[] args) {
        QueryStationImpl queryStationImpl = null;
        
        try (Communicator communicator = Util.initialize(args, "config.query.cfg");
             Scanner scanner = new Scanner(System.in)) {

            Properties props = communicator.getProperties();
            String proxyCacheProxy = props.getProperty("ProxyCache.Proxy");
            ObjectPrx proxyCacheBase = communicator.stringToProxy(proxyCacheProxy);
            ServerQueryServicePrx proxyCacheService = ServerQueryServicePrx.checkedCast(proxyCacheBase);
            ObjectAdapter adapter = communicator.createObjectAdapter("QueryStationAdapter");
            
            queryStationImpl = new QueryStationImpl(proxyCacheService);
            adapter.add(queryStationImpl, Util.stringToIdentity("QueryStation"));
            adapter.activate();
            
            queryStationProxy = queryStationPrx.uncheckedCast(
                adapter.createProxy(Util.stringToIdentity("QueryStation"))
            );
            
            startCLI(scanner);
            
        } catch (Exception e) {
            System.err.println("Error en QueryStation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (queryStationImpl != null) {
                queryStationImpl.shutdown();
            }
            System.out.println("QueryStation terminado");
        }
    }

    private static void startCLI(Scanner scanner) {
        boolean running = true;
        System.out.println("\nQuery Station CLI");
        System.out.println("Commands: query <document> | exit");
        
        while (running) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");
            String command = parts.length > 0 ? parts[0].toLowerCase() : "";
            
            try {
                switch (command) {
                    case "query":
                        if (parts.length == 2) {
                            handleSingleQuery(parts[1]);
                        } else {
                            System.err.println("Usage: query <document>");
                        }
                        break;
                        
                    case "exit":
                        running = false;
                        break;
                        
                    default:
                        if (!command.isEmpty()) {
                            System.err.println("Unknown command: " + command);
                        }
                        System.out.println("Available commands: query <document> | exit");
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
            }
        }
        
        System.out.println("Shutting down Query Station CLI...");
    }
    
    private static void handleSingleQuery(String document) {
        try {
            long startTime = System.currentTimeMillis();

            String result = queryStationProxy.query(document);
            
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            System.out.println("Response time: " + responseTime + "ms");
            if (result != null && !result.trim().isEmpty()) {
                System.out.println("Voting station found: " + result);
            } else {
                System.out.println("Citizen not found for document: " + document);
            }
            
        } catch (Exception e) {
            System.err.println("Error querying document " + document + ": " + e.getMessage());
        }
    }
}