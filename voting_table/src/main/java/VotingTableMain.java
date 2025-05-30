import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ControlCenterServicePrx;
// import ElectionSystem.VotingTableService; // No es necesario, VotingTableImpl lo implementa

public class VotingTableMain {

    public static void main(String[] args) {
        // El ID de esta mesa de votación particular. Esto debería ser único por instancia.
        // Podría venir de un argumento de línea de comandos, una variable de entorno o un archivo de configuración específico de la instancia.
        // Por ahora, lo codificaremos, pero en un despliegue real esto debe ser dinámico.
        String tableIdStr = System.getProperty("VOTING_TABLE_ID", "Table1"); // Ejemplo: obtener de propiedad del sistema
        // Necesitaremos una forma de obtener un ID numérico si la lógica de negocio lo requiere,
        // o usar el string ID para la identidad del objeto Ice.

        try (Communicator communicator = Util.initialize(args, "config.votetable.cfg")) {
            
            // Como cliente: Obtener ControlCenterServicePrx de IceGrid
            ControlCenterServicePrx controlCenterService = ControlCenterServicePrx.checkedCast(
                communicator.stringToProxy("ControlCenterService"));

            if (controlCenterService == null) {
                System.err.println("Error: Could not get a proxy for ControlCenterService from IceGrid. Check locator and if service is running.");
                return;
            }
            System.out.println("Successfully obtained ControlCenterServicePrx from IceGrid.");

            // Como servidor: Ofrecer VotingTableService
            // Usar un nombre de adaptador, ej. "VotingTableAdapter"
            ObjectAdapter adapter = communicator.createObjectAdapter("VotingTableAdapter");
            
            VotingTableImpl votingTableImpl = new VotingTableImpl(controlCenterService);
            
            // La identidad debe ser única para cada instancia de VotingTableService.
            // Usaremos el tableIdStr para esto.
            adapter.add(votingTableImpl, Util.stringToIdentity("VotingTableService-" + tableIdStr));
            
            adapter.activate();
            System.out.println("VotingTableService ready and registered with adapter VotingTableAdapter under identity 'VotingTableService-" + tableIdStr + "'.");
            
            communicator.waitForShutdown();

        } catch (com.zeroc.Ice.LocalException e) {
            System.err.println("Ice Local Exception in VotingTableMain for table '" + tableIdStr + "': " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred in VotingTableMain for table '" + tableIdStr + "': " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("VotingTableMain for '" + tableIdStr + "' shut down.");
    }
}
