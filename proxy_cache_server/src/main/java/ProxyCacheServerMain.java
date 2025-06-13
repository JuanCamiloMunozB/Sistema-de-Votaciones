import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;

import ElectionSystem.*;

public class ProxyCacheServerMain {
    
    public static void main(String[] args) {
        ProxyCacheService proxyCacheService = null;
        
        try (Communicator communicator = Util.initialize(args, "config.proxy.cfg")) {
            System.out.println("=== Proxy Cache Server (Independiente) ===");
            System.out.println("Intermediario para alta disponibilidad");
            System.out.println("Conectando a ServerService via IceGrid...");
            
            // Conectar al servidor principal usando IceGrid (solo para localizar)
            ObjectPrx serverBase = communicator.stringToProxy("ServerService");
            ServerServicePrx serverProxy = ServerServicePrx.checkedCast(serverBase);
            
            if (serverProxy == null) {
                System.err.println("Error: No se pudo conectar al ServerService a través de IceGrid");
                System.err.println("Asegúrate de que el servidor principal esté ejecutándose");
                return;
            }
            
            System.out.println("Conectado al ServerService a través de IceGrid Registry");
            
            // Crear adapter SIN registrar en IceGrid
            ObjectAdapter adapter = communicator.createObjectAdapter("ProxyCacheAdapter");
            
            // Instanciar y registrar el servicio proxy cache localmente
            proxyCacheService = new ProxyCacheService(serverProxy, communicator);
            adapter.add(proxyCacheService, Util.stringToIdentity("ProxyCache"));
            adapter.activate();
            
            System.out.println("Proxy Cache activo como servidor independiente");
            System.out.println("Disponible en: tcp -h localhost -p 9091");
            System.out.println("Cache configurado (15 min expiry, 10K max size)");
            System.out.println("Conectado a múltiples servidores backend via IceGrid");
            System.out.println("Proxy Cache listo para intermediar consultas...");
            
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