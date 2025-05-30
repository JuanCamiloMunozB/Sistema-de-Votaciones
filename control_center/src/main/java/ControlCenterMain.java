import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ServerServicePrx;
// import ElectionSystem.ControlCenterService; // No es necesario ControlCenterService aquí, ya que ControlCenterImpl lo implementa

public class ControlCenterMain {
    
    public static void main(String[] args) {
        String controlCenterId = System.getProperty("CONTROL_CENTER_ID", "ControlCenter1"); // Hacerlo configurable

        try(Communicator communicator = Util.initialize(args, "config.control.cfg")) {
            
            ServerServicePrx serverService = ServerServicePrx.checkedCast(
                communicator.stringToProxy("ServerService"));

            if (serverService == null) {
                System.err.println("Error: Could not get a proxy for ServerService from IceGrid. Check locator configuration and if ServerService is running and registered.");
                return;
            }
            System.out.println("Successfully obtained ServerServicePrx from IceGrid.");

            // El ObjectAdapter se crea aquí y se pasa a ControlCenterImpl
            ObjectAdapter adapter = communicator.createObjectAdapter("ControlCenterAdapter");
            
            // Crear la instancia de ControlCenterImpl, pasando el adapter y un ID único
            ControlCenterImpl controlCenterImpl = new ControlCenterImpl(serverService, controlCenterId, adapter);
            
            // Añadir el sirviente ControlCenterService al adaptador
            adapter.add(controlCenterImpl, Util.stringToIdentity("ControlCenterService")); 
            // El sirviente EventObserver se añade dentro de initializeSubscription() llamado abajo
            
            adapter.activate(); // Activar el adaptador ANTES de que el observador intente usarlo para crear su proxy
            System.out.println("ControlCenterService ready and registered with adapter ControlCenterAdapter under identity 'ControlCenterService'.");
            
            // Llamar al método para que ControlCenterImpl se suscriba y active su observador
            controlCenterImpl.initializeSubscription();

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
        System.out.println("ControlCenterMain for '" + controlCenterId + "' shut down.");
    }
}
