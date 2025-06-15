import ElectionSystem.*;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.NotRegisteredException;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;
import com.zeroc.Ice.ObjectAdapterDeactivatedException;
import com.zeroc.Ice.ConnectTimeoutException;
import com.zeroc.Ice.ConnectionRefusedException;
import com.zeroc.Ice.UnknownException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


public class ControlCenterImpl implements ControlCenterService {
    private ServerServicePrx serverService;
    private String controlCenterId;
    private ObjectAdapter adapter;
    Queue<VoteData> pendingVotes = new LinkedList<>();
    private Timer retryTimer = new Timer();
    private static final String PENDING_VOTES_FILE = "pending_votes.csv";
    private final Set<String> pendingKeys = new HashSet<>();
    private static final long RETRY_INTERVAL_MS = 60000;
    private EventObserverI observerServant;
    private final Map<String, ElectionActivityObserverPrx> electionActivitySubscribers = new ConcurrentHashMap<>();
    private boolean electionActive = false;

    public ControlCenterImpl(ServerServicePrx serverService, String controlCenterId, ObjectAdapter adapter) {
        this.serverService = serverService;
        this.controlCenterId = controlCenterId;
        this.adapter = adapter;
        loadPendingVotesFromDisk();
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
                System.out.println("ControlCenter [" + this.controlCenterId + "] observerPrx: " + observerPrx.toString());
                this.serverService.subscribe(observerPrx, observerIceIdentity.name);
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

    private static class EventObserverI implements EventObserver {
        private String ownerControlCenterId;
        public EventObserverI(String ownerId) {
            this.ownerControlCenterId = ownerId;
        }

        @Override
        public void _notify(ElectionEvent event, Current current) {
            // Process notification in separate thread to avoid blocking
            new Thread(() -> {
                try {
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
                } catch (Exception e) {
                    System.err.println("Error in EventObserver._notify for ControlCenter '" + this.ownerControlCenterId + "': " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }).start();
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

    private void loadPendingVotesFromDisk() {
        File file = new File(PENDING_VOTES_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 4);
                if (parts.length < 4) {
                    continue;
                }

                String citizenDocument = parts[0].trim();
                int candidateId = Integer.parseInt(parts[1].trim());
                int tableId = Integer.parseInt(parts[2].trim());
                String timestamp = parts[3].trim();

                VoteData vote = new VoteData(citizenDocument, candidateId, tableId, timestamp);
                pendingVotes.add(vote);

                String key = buildVoteKey(citizenDocument, candidateId, tableId);
                pendingKeys.add(key);
            }
            System.out.println(
                "ControlCenter [" + controlCenterId + "]: Loaded " +
                pendingVotes.size() + " pending votes from disk."
            );
        } catch (IOException e) {
            System.err.println(
                "ControlCenter [" + controlCenterId +
                "]: Error reading pending votes file: " + e.getMessage()
            );
        }
    }


    
    private String buildVoteKey(String citizenDocument, int candidateId, int tableId) {
        return citizenDocument + "|" + candidateId + "|" + tableId;
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
            dequeuePendingVote();
            System.out.println("ControlCenter [" + controlCenterId + "]: Pending vote sent successfully for " 
                                + vote.citizenDocument);
        } catch (ConnectTimeoutException | ConnectionRefusedException | NotRegisteredException e) {
            System.err.println("ControlCenter [" + controlCenterId
                               + "]: Failed to send pending vote, server unavailable. Will retry later: "
                               + e.getMessage());
            break;
        } catch (Exception e) {
            System.err.println("ControlCenter [" + controlCenterId 
                               + "]: Error sending pending vote (definitivo), removing from queue: " 
                               + e.getMessage());
            dequeuePendingVote(); 
            }
        }
    }


    private synchronized void savePendingVotesToDisk() {
    File file = new File(PENDING_VOTES_FILE);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, false))) {
            for (VoteData vote : pendingVotes) {
            String line = String.format("%s,%d,%d,%s",
                    vote.citizenDocument,
                    vote.candidateId,
                    vote.tableId,
                    vote.timestamp);
            bw.write(line);
            bw.newLine();
            }
        bw.flush();
    } catch (IOException e) {
        System.err.println("ControlCenter [" + controlCenterId + "]: Error guardando votos pendientes en disco: " + e.getMessage());
        }
    }


    private synchronized VoteData dequeuePendingVote() {
    VoteData head = pendingVotes.poll();
    if (head != null) {
        String key = buildVoteKey(head.citizenDocument, head.candidateId, head.tableId);
        pendingKeys.remove(key);
        savePendingVotesToDisk();
        }
    return head;
    }   


    @Override
    public VotingTableData getVotingTableData(int tableId, Current current) {
        throw new UnsupportedOperationException("Unimplemented method 'getVotingTableData'");
    }

    @Override
    public void startElection(Current current) {
        System.out.println("ControlCenter [" + controlCenterId + "]: Iniciando elección.");
        notifyElectionStarted();
        electionActive = true; // Cambiar el estado a activo
    }
    @Override
    public void endElection(Current current) {
        System.out.println("ControlCenter [" + controlCenterId + "]: Finalizando elección.");
        notifyElectionEnded();
        exportResultsToCSV();
        electionActive = false; // Cambiar el estado a inactivo
    }
    @Override
    public void submitVote(VoteData vote, Current current) throws CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable, ElectionInactive {
        if (!electionActive) {
            throw new ElectionInactive("Election is not currently active. Cannot submit vote.");
        }

        try {
            serverService.registerVote(vote);
            System.out.println("Vote submitted successfully for document: " + vote.citizenDocument);
            
        } catch (CitizenAlreadyVoted e) {
            throw e;
        } catch (CitizenNotFound e) {
            throw e;
        } catch (CandidateNotFound e) {
            throw e;
        } catch (CitizenNotBelongToTable e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            
            if (errorMessage.contains("queue is full") || 
                errorMessage.contains("maximum capacity") ||
                errorMessage.contains("pending queue")) {
                System.out.println("Vote submitted successfully for document: " + vote.citizenDocument);
                return;
            }
            
            e.printStackTrace(System.err);
            System.err.println("Failed to submit vote due to an unexpected server error: " + e.getMessage());
            throw new UnknownException(e); 
        }
    }

    private void notifyElectionStarted() {
        System.out.println("ControlCenter [" + controlCenterId + "]: Notificando inicio de elección a " + electionActivitySubscribers.size() + " mesas.");
        for (Map.Entry<String, ElectionActivityObserverPrx> entry : electionActivitySubscribers.entrySet()) {
            String identity = entry.getKey();
            ElectionActivityObserverPrx observer = entry.getValue();
            try {
                observer.electionStarted();
                System.out.println("ControlCenter [" + controlCenterId + "]: Notificación de inicio enviada a " + identity);
            } catch (com.zeroc.Ice.ObjectNotExistException | com.zeroc.Ice.CommunicatorDestroyedException e) {
                System.err.println("ControlCenter [" + controlCenterId + "]: Observador de actividad '" + identity + "' parece estar abajo. Removiendo: " + e.getMessage());
                electionActivitySubscribers.remove(identity);
            } catch (Exception e) {
                System.err.println("ControlCenter [" + controlCenterId + "]: Falló al notificar inicio a '" + identity + "': " + e.getMessage());
            }
        }
    }

    private void notifyElectionEnded() {
        System.out.println("ControlCenter [" + controlCenterId + "]: Notificando fin de elección a " + electionActivitySubscribers.size() + " mesas.");
        for (Map.Entry<String, ElectionActivityObserverPrx> entry : electionActivitySubscribers.entrySet()) {
            String identity = entry.getKey();
            ElectionActivityObserverPrx observer = entry.getValue();
            try {
                observer.electionEnded();
                System.out.println("ControlCenter [" + controlCenterId + "]: Notificación de fin enviada a " + identity);
            } catch (com.zeroc.Ice.ObjectNotExistException | com.zeroc.Ice.CommunicatorDestroyedException e) {
                System.err.println("ControlCenter [" + controlCenterId + "]: Observador de actividad '" + identity + "' parece estar abajo. Removiendo: " + e.getMessage());
                electionActivitySubscribers.remove(identity);
            } catch (Exception e) {
                System.err.println("ControlCenter [" + controlCenterId + "]: Falló al notificar fin a '" + identity + "': " + e.getMessage());
            }
        }
    }

    @Override
    public void subscribeElectionActivity(ElectionActivityObserverPrx observer, String votingTableIdentity, Current current) {
        if (observer == null || votingTableIdentity == null || votingTableIdentity.isEmpty()) {
            System.err.println("ControlCenter [" + controlCenterId + "]: Intento de suscripción de actividad electoral con observador nulo o identidad vacía.");
            return;
        }
        electionActivitySubscribers.put(votingTableIdentity, observer);
        System.out.println("ControlCenter [" + controlCenterId + "]: Mesa de votación '" + votingTableIdentity + "' suscrita a actividad electoral.");
    }

    @Override
    public void unsubscribeElectionActivity(String votingTableIdentity, Current current) {
        if (votingTableIdentity == null || votingTableIdentity.isEmpty()) {
            System.err.println("ControlCenter [" + controlCenterId + "]: Intento de desuscripción de actividad electoral con identidad vacía.");
            return;
        }
        ElectionActivityObserverPrx removed = electionActivitySubscribers.remove(votingTableIdentity);
        if (removed != null) {
            System.out.println("ControlCenter [" + controlCenterId + "]: Mesa de votación '" + votingTableIdentity + "' desuscrita de actividad electoral.");
        } else {
            System.out.println("ControlCenter [" + controlCenterId + "]: Mesa de votación '" + votingTableIdentity + "' no encontrada para desuscripción.");
        }
    }

    @Override
    public CitizenData[] getCitizensByTableId(int tableId, Current current) {
        try {
            return serverService.getCitizensByTableId(tableId);
        } catch (Exception e) {
            System.err.println("ControlCenter [" + controlCenterId + "]: Error getting citizens for table " + tableId + ": " + e.getMessage());
            return new CitizenData[0];
        }
    }

    private void exportResultsToCSV() {
    try {
        System.out.println("Exportando resultados...");

        // Archivo global
        CandidateResult[] globalResults = serverService.getGlobalResults();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("resume.csv"))) {
            writer.write("candidateId,candidateName,totalVotes\n");
            for (CandidateResult r : globalResults) {
                writer.write(String.format("%d,%s,%d\n", r.candidateId, r.candidateName, r.totalVotes));
            }
        }

        // Archivos por mesa
        Map<Integer, CandidateResult[]> perTableResults = serverService.getResultsByVotingTable();
        for (Map.Entry<Integer, CandidateResult[]> entry : perTableResults.entrySet()) {
            int tableId = entry.getKey();
            CandidateResult[] tableResults = entry.getValue();
            String fileName = "partial-" + tableId + ".csv";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                writer.write("candidateId,candidateName,totalVotes\n");
                for (CandidateResult r : tableResults) {
                    writer.write(String.format("%d,%s,%d\n", r.candidateId, r.candidateName, r.totalVotes));
                }
            }
        }

        System.out.println(" Exportación de resultados completada.");
    } catch (Exception e) {
        System.err.println(" Error al exportar resultados: " + e.getMessage());
        e.printStackTrace();
    }
    }

}
