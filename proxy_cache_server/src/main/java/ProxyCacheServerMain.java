import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;

import ElectionSystem.*;

public class ProxyCacheServerMain {
    
    public static void main(String[] args) {
        ProxyCacheService proxyCacheService = null;
        
        try (Communicator communicator = Util.initialize(args, "config.proxy.cfg")) {
            // Conectar al servidor principal usando IceGrid
            ObjectPrx serverBase = communicator.stringToProxy("ServerService");
            ServerServicePrx serverProxy = ServerServicePrx.checkedCast(serverBase);

            ObjectAdapter adapter = communicator.createObjectAdapter("ProxyCacheAdapter");            
            proxyCacheService = new ProxyCacheService(serverProxy, communicator);
            adapter.add(proxyCacheService, Util.stringToIdentity("ProxyCache"));
            adapter.activate();

            System.out.println("Proxy Cache Server iniciado y esperando consultas...");
            
            communicator.waitForShutdown();
            
        } catch (Exception e) {
            System.err.println("Error en Proxy Cache: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (proxyCacheService != null) {
                proxyCacheService.shutdown();
            }
            System.out.println("Proxy Cache terminado");
        }
    }
}