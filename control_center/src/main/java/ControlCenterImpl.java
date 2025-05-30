import ElectionSystem.*;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;
import com.zeroc.Ice.ObjectAdapterDeactivatedException;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

public class ControlCenterImpl implements ControlCenterService {
    // ... (campos como antes)
    private ServerServicePrx serverService;
    private String controlCenterId;
    private ObjectAdapter adapter;
    Queue<VoteData> pendingVotes = new LinkedList<>();
    private Timer retryTimer = new Timer();
    private static final long RETRY_INTERVAL_MS = 60000;
    private EventObserverI observerServant;

    // ... (constructor como antes)
    public ControlCenterImpl(ServerServicePrx serverService, String controlCenterId, ObjectAdapter adapter) {
        this.serverService = serverService;
        this.controlCenterId = controlCenterId;
        this.adapter = adapter;
        startRetryTask();
        this.observerServant = new EventObserverI(this.controlCenterId);
    }

    public void initializeSubscription() {
        if (this.serverService == null) {
            System.err.println("ControlCenter [" + controlCenterId + "]: serverService es nulo, no se puede suscribir.");
            return;
        }
        if (this.adapter == null) {
            System.err.println("ControlCenter [" + controlCenterId + "]: adapter es nulo, no se puede registrar el observador.");
            return;
        }
        if (this.observerServant == null) {
            System.err.println("ControlCenter [" + controlCenterId + "]: observerServant es nulo.");
            return;
        }

        try {
            Identity observerIceIdentity = Util.stringToIdentity(this.controlCenterId + "Observer");
            ObjectPrx observerProxyBase = this.adapter.add(this.observerServant, observerIceIdentity);
            EventObserverPrx observerPrx = EventObserverPrx.checkedCast(observerProxyBase);

            if (observerPrx != null) {
                System.out.println("ControlCenter [" + this.controlCenterId + "] intentando suscribirse con observador '" + observerIceIdentity.name + "'...");
                this.serverService.subscribe( observerPrx, observerIceIdentity.name);
                System.out.println("ControlCenter [" + this.controlCenterId + "] suscrito exitosamente a eventos.");
            } else {
                System.err.println("Error: ControlCenter [" + this.controlCenterId + "] no pudo crear el proxy del observador para la suscripción.");
            }
        } catch (ObjectAdapterDeactivatedException e) {
             System.err.println("Error durante la suscripción de ControlCenter [" + this.controlCenterId + "]: El adaptador de objetos fue desactivado. " + e.getMessage());
             e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error durante la suscripción de ControlCenter [" + this.controlCenterId + "]: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Clase interna EventObserverI
    private static class EventObserverI implements EventObserver {
        private String ownerControlCenterId;
        public EventObserverI(String ownerId) {
            this.ownerControlCenterId = ownerId;
        }

        @Override
        public void _notify(ElectionEvent event, Current current) {
            System.out.println("ControlCenter Observer ('" + this.ownerControlCenterId + "') recibió evento: " + event.type.name());
            System.out.println("  Timestamp: " + event.timestamp);
            if (event.details != null) {
                 System.out.println("  Details: " + event.details);
                switch (event.type) {
                    case VoteRegistered:
                        String citizenId = event.details.get("citizenId");
                        String candidateId = event.details.get("candidateId");
                        System.out.println("  Detalle de Voto Registrado: Ciudadano=" + citizenId + ", Candidato=" + candidateId);
                        break;
                    case ElectionStarted:
                        System.out.println("  La Elección ha comenzado: " + event.details.get("electionName"));
                        break;
                    case ElectionEnded:
                        System.out.println("  La Elección ha terminado: " + event.details.get("electionName"));
                        break;
                    default:
                        System.out.println("  Tipo de evento desconocido (" + event.type.name() + ") recibido.");
                        break;
                }
            } else {
                System.out.println("  Details: (null)");
            }
        }
    }

    private void startRetryTask() {
        retryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                processPendingVotes();
            }
        }, RETRY_INTERVAL_MS, RETRY_INTERVAL_MS);
    }

    @Override
    public CandidateData[] getCandidates(Current current) {
        return serverService.getCandidates();
    }

    synchronized void processPendingVotes() {
        while (!pendingVotes.isEmpty()) {
            VoteData vote = pendingVotes.peek();
            try {
                serverService.registerVote(vote);
                pendingVotes.poll();
                System.out.println("Pending vote sent successfully: Citizen " + vote.citizenId);
            } catch (com.zeroc.Ice.ConnectTimeoutException | com.zeroc.Ice.ConnectionRefusedException e) {
                System.err.println("Failed to send pending vote, server unavailable. Will retry later: " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("Error sending pending vote: " + e.getMessage());
                pendingVotes.poll();
            }
        }
    }

    @Override
    public VotingTableData getVotingTableData(int tableId, Current current) {
        throw new UnsupportedOperationException("Unimplemented method 'getVotingTableData'");
    }

    @Override
    public void startElection(Current current) {
        System.out.println("ControlCenter: Solicitud de inicio de elección recibida.");
        throw new UnsupportedOperationException("Unimplemented method 'startElection' para publicación de evento");
    }
    @Override
    public void endElection(Current current) {
        System.out.println("ControlCenter: Solicitud de fin de elección recibida.");
        throw new UnsupportedOperationException("Unimplemented method 'endElection' para publicación de evento");
    }
    @Override
    public void submitVote(VoteData vote, Current current) {
        try {
            serverService.registerVote(vote);
            System.out.println("Vote submitted successfully for citizen: " + vote.citizenId);
        } catch (com.zeroc.Ice.ConnectTimeoutException | com.zeroc.Ice.ConnectionRefusedException e) {
            System.err.println("Server unavailable, adding vote to pending queue: " + e.getMessage());
            synchronized (this) {
                pendingVotes.add(vote);
            }
        } catch (Exception e) {
            System.err.println("Failed to submit vote: " + e.getMessage());
            throw e;
        }
    }
}
