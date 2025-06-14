import ElectionSystem.*;

import com.zeroc.Ice.Current;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class VotingTableImpl implements VotingTableCombinedService {

    private ControlCenterServicePrx controlCenterService;
    private String votingTableId;
    private AtomicBoolean electionActive = new AtomicBoolean(false);

    public VotingTableImpl(ControlCenterServicePrx controlCenterService, String votingTableId) {
        this.controlCenterService = controlCenterService;
        this.votingTableId = votingTableId;
    }

    @Override
    public void electionStarted(Current current) {
        electionActive.set(true);
        System.out.println("VotingTable [" + votingTableId + "]: Election has started. Accepting votes.");
    }

    @Override
    public void electionEnded(Current current) {
        electionActive.set(false);
        System.out.println("VotingTable [" + votingTableId + "]: Election has ended. Not accepting votes.");
    }
    
    @Override
    public void emitVote(VoteData vote, Current current) throws ElectionInactive, CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable {
        if (!electionActive.get()) {
            System.out.println("VotingTable [" + votingTableId + "]: Election is not active. Vote rejected.");
            throw new ElectionInactive("Election is not currently active.");
        }
        try {
            System.out.println("VotingTable [" + votingTableId + "]: Attempting to emit vote for citizen: " + vote.citizenDocument);
            controlCenterService.submitVote(vote);
            System.out.println("VotingTable [" + votingTableId + "]: Vote emitted successfully for citizen: " + vote.citizenDocument);
        } catch (CitizenAlreadyVoted | CitizenNotFound | CandidateNotFound | CitizenNotBelongToTable e) {
            System.err.println("VotingTable [" + votingTableId + "]: Error emitting vote: " + e.ice_name() + " - " + e.getMessage());
            throw e;
        } catch (com.zeroc.Ice.CommunicatorDestroyedException | com.zeroc.Ice.ObjectNotExistException e) {
            System.err.println("VotingTable [" + votingTableId + "]: Control Center seems to be down. Could not emit vote. " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("VotingTable [" + votingTableId + "]: An unexpected error occurred while emitting vote to Control Center: " + e.getMessage());
            throw new com.zeroc.Ice.UnknownException(e);
        }
    }

    public String getVotingTableId() {
        return votingTableId;
    }

    public boolean isElectionActive() {
        return electionActive.get();
    }

    @Override
    public int vote(String document, int candidateId, Current current) {
        try {
            // Verificar si la elección está activa
            if (!electionActive.get()) {
                System.out.println("VotingTable [" + votingTableId + "]: Election is not active for document: " + document);
                return 0; // Podría votar si la elección estuviera activa, pero no está
            }

            // Extraer el ID numérico de la mesa de votación
            int numericTableId;
            try {
                String numericPart = votingTableId.replaceAll("[^0-9]", "");
                numericTableId = numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
            } catch (NumberFormatException e) {
                System.err.println("VotingTable [" + votingTableId + "]: Error parsing table ID");
                numericTableId = 0;
            }

            // Crear el VoteData con timestamp actual
            VoteData vote = new VoteData(
                document, 
                candidateId, 
                numericTableId, 
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            );

            // Intentar enviar el voto a través del ControlCenter
            controlCenterService.submitVote(vote);
            
            System.out.println("VotingTable [" + votingTableId + "]: Vote successful for document: " + document);
            return 0; // Voto exitoso
            
        } catch (CitizenNotFound e) {
            System.err.println("VotingTable [" + votingTableId + "]: Citizen not found: " + document);
            return 3; // No aparece en toda la BD
            
        } catch (CitizenNotBelongToTable e) {
            System.err.println("VotingTable [" + votingTableId + "]: Citizen does not belong to this table: " + document);
            return 1; // No es su mesa
            
        } catch (CitizenAlreadyVoted e) {
            System.err.println("VotingTable [" + votingTableId + "]: Citizen already voted: " + document);
            return 2; // Está tratando de votar por segunda vez
            
        } catch (CandidateNotFound e) {
            System.err.println("VotingTable [" + votingTableId + "]: Candidate not found: " + candidateId);
            return 0; // El ciudadano puede votar, pero el candidato no existe
            
        } catch (ElectionInactive e) {
            System.err.println("VotingTable [" + votingTableId + "]: Election is inactive: " + document);
            return 0; // Podría votar si la elección estuviera activa
            
        } catch (Exception e) {
            System.err.println("VotingTable [" + votingTableId + "]: Unexpected error for document " + document + ": " + e.getMessage());
            e.printStackTrace();
            return 0; // Error genérico, asumimos que podría votar en condiciones normales
        }
    }
}