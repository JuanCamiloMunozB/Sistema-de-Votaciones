import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ServerServicePrx;
// import ElectionSystem.ControlCenterService; // No es necesario ControlCenterService aquí, ya que ControlCenterImpl lo implementa

public class ControlCenterMain {
    
    public static void main(String[] args) {
        try(Communicator communicator = Util.initialize(args, "config.control.cfg")) {
            
            ServerServicePrx serverService = ServerServicePrx.checkedCast(
                communicator.stringToProxy("ServerService"));

            if (serverService == null) {
                System.err.println("Error: Could not get a proxy for ServerService from IceGrid. Check locator configuration and if ServerService is running and registered.");
                return;
            }
            System.out.println("Successfully obtained ServerServicePrx from IceGrid.");

            ObjectAdapter adapter = communicator.createObjectAdapter("ControlCenterAdapter");
            
            ControlCenterImpl controlCenterImpl = new ControlCenterImpl(serverService);
            
            adapter.add(controlCenterImpl, Util.stringToIdentity("ControlCenterService"));
            
            adapter.activate();
            System.out.println("ControlCenterService ready and registered with adapter ControlCenterAdapter under identity 'ControlCenterService'.");
            
            communicator.waitForShutdown();

        } catch (com.zeroc.Ice.LocalException e) {
            // Errores locales de Ice, incluyendo problemas de conexión o configuración del locator
            System.err.println("Ice Local Exception in ControlCenterMain: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Otras excepciones inesperadas
            System.err.println("An unexpected error occurred in ControlCenterMain: " + e.getMessage());
            e.printStackTrace();
        }
        // El communicator.destroy() es llamado automáticamente por el try-with-resources
        System.out.println("ControlCenterMain shut down.");
    }
}
