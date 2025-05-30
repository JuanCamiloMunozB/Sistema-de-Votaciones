import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import com.zeroc.IceGrid.QueryPrx;

import utils.JPAUtil;
import repositories.elections.*;
import repositories.votaciones.*;

public class ServerMain {
    
    public static void main(String[] args) {
        Communicator communicator = null; // Declarar fuera para que esté disponible en finally
        try {
            communicator = Util.initialize(args, "config.server.cfg");
            JPAUtil.initialize(communicator); // Corregido: pasar el comunicador

            // El adaptador debe tener un nombre que IceGrid conozca, o IceGrid asignará uno.
            // Si el descriptor de IceGrid especifica <adapter name="ServerAdapter" ...>, usa ese nombre.
            // Si no, IceGrid podría usar un nombre por defecto o uno basado en la identidad del servidor.
            // Para el enfoque donde el servidor crea su propio adaptador y IceGrid lo descubre/gestiona indirectamente:
            ObjectAdapter adapter = communicator.createObjectAdapter("ServerAdapter"); // Usar un nombre de adaptador consistente.

            ElectionRepository electionRepository = new ElectionRepository();
            CandidateRepository candidateRepository = new CandidateRepository();
            VoteRepository voteRepository = new VoteRepository();
            CitizenRepository citizenRepository = new CitizenRepository();
            VotingTableRepository votingTableRepository = new VotingTableRepository();

            adapter.add(new ServerImpl(electionRepository, candidateRepository, voteRepository, citizenRepository, votingTableRepository), 
                        Util.stringToIdentity("ServerService")); // Esta es la identidad lógica que los clientes usarán.
            
            adapter.activate();
            System.out.println("ServerService ready and registered with adapter ServerAdapter.");

            // Opcional: Notificar a IceGrid que el servidor está listo (si no usa replicación gestionada por IceGrid)
            // QueryPrx iceGridQuery = QueryPrx.checkedCast(communicator.stringToProxy("IceGrid/Query"));
            // if (iceGridQuery != null) {
            //     iceGridQuery.addObjectWithType(Util.stringToIdentity("ServerService"), "::ElectionSystem::ServerService");
            //     System.out.println("ServerService identity published to IceGrid/Query (simple registration).");
            // }
            
            communicator.waitForShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (communicator != null) { // Asegurar que el comunicador fue inicializado
                 JPAUtil.shutdown(); 
            }
        }
    }
}