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
            
            System.out.println("=== Query Station Client (Independiente) ===");
            System.out.println("Target: 2,666+ consultas por segundo");
            System.out.println("Conectando al Proxy Cache...");
            
            // Leer configuración del Proxy Cache desde archivo
            Properties props = communicator.getProperties();
            String proxyCacheProxy = props.getProperty("ProxyCache.Proxy");
            
            if (proxyCacheProxy == null || proxyCacheProxy.trim().isEmpty()) {
                System.err.println("❌ Error: ProxyCache.Proxy no está configurado en config.query.cfg");
                return;
            }
            
            System.out.println("Usando configuración: " + proxyCacheProxy);
            
            // Conectar usando configuración
            ObjectPrx proxyCacheBase = communicator.stringToProxy(proxyCacheProxy);
            ServerQueryServicePrx proxyCacheService = ServerQueryServicePrx.checkedCast(proxyCacheBase);
            
            if (proxyCacheService == null) {
                System.err.println("Error: No se pudo conectar al Proxy Cache");
                System.err.println("Configuración: " + proxyCacheProxy);
                System.err.println("Asegúrate de que Proxy Cache esté ejecutándose");
                return;
            }
            
            System.out.println("Conectado al Proxy Cache usando configuración");
            
            // Crear adapter local
            ObjectAdapter adapter = communicator.createObjectAdapter("QueryStationAdapter");
            
            // Instanciar servicio
            queryStationImpl = new QueryStationImpl(proxyCacheService);
            
            // Registrar servicio LOCALMENTE
            adapter.add(queryStationImpl, Util.stringToIdentity("QueryStation"));
            adapter.activate();
            
            // Crear proxy para CLI
            queryStationProxy = queryStationPrx.uncheckedCast(
                adapter.createProxy(Util.stringToIdentity("QueryStation"))
            );
            
            System.out.println("QueryStation activo como cliente independiente");
            System.out.println("Puerto local: 9092");
            System.out.println("Configuración leída de: config.query.cfg");
            System.out.println("QueryStation listo para consultas...");
            
            // Iniciar CLI integrada
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
            System.out.println("Querying document: " + document);
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