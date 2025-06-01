import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ControlCenterServicePrx;
import ElectionSystem.ElectionActivityObserverPrx; 
import ElectionSystem.VoteData; 
import ElectionSystem.CandidateData; 
import ElectionSystem.ElectionInactive; 

import java.util.Scanner; 
import java.time.LocalDateTime; 
import java.time.format.DateTimeFormatter; 

public class VotingTableMain {

    public static void main(String[] args) {
        String tableIdStr = System.getProperty("VOTING_TABLE_ID", "Table1");
        int numericTableId; 
        try {
            String numericPart = tableIdStr.replaceAll("[^0-9]", "");
            if (numericPart.isEmpty()) {
                 System.err.println("Could not parse a numeric ID from tableId: " + tableIdStr + ". Defaulting to 0 or handle error.");
                 numericTableId = 0; 
            } else {
                numericTableId = Integer.parseInt(numericPart);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing numeric table ID from '" + tableIdStr + "'. Please ensure VOTING_TABLE_ID is in a valid format (e.g., 'Table1').");
            e.printStackTrace();
            return;
        }


        try (Communicator communicator = Util.initialize(args, "config.voting.cfg");
             Scanner scanner = new Scanner(System.in)) { 
            
            ControlCenterServicePrx controlCenterService = ControlCenterServicePrx.checkedCast(
                communicator.stringToProxy("ControlCenterService"));

            if (controlCenterService == null) {
                System.err.println("Error: Could not get a proxy for ControlCenterService from IceGrid.");
                return;
            }
            System.out.println("Successfully obtained ControlCenterServicePrx from IceGrid.");

            ObjectAdapter adapter = communicator.createObjectAdapter("VotingTableAdapter");
            VotingTableImpl votingTableImpl = new VotingTableImpl(controlCenterService, tableIdStr);
            adapter.add(votingTableImpl, Util.stringToIdentity("VotingTableService-" + tableIdStr));
            adapter.activate();
            System.out.println("VotingTableService ready (" + tableIdStr + ").");
            
            try {
                ElectionActivityObserverPrx observerPrx = 
                    ElectionActivityObserverPrx.uncheckedCast(
                        adapter.createProxy(Util.stringToIdentity("VotingTableService-" + tableIdStr))
                    );
                if (observerPrx != null) {
                    controlCenterService.subscribeElectionActivity(observerPrx, tableIdStr);
                    System.out.println("VotingTable [" + tableIdStr + "] subscribed to election activity events.");
                } else {
                    System.err.println("VotingTable [" + tableIdStr + "] could not create observer proxy for subscription.");
                }
            } catch (Exception e) {
                System.err.println("VotingTable [" + tableIdStr + "] failed to subscribe: " + e.getMessage());
            }

            boolean running = true;
            System.out.println("\nVoting Table UI (" + tableIdStr + ")");
            System.out.println("Commands: vote <citizenDocument> <candidateId> | candidates | status | exit");

            while(running) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                String[] parts = line.split("\\s+");
                String command = parts.length > 0 ? parts[0].toLowerCase() : "";

                try {
                    switch(command) {
                        case "vote":
                            if (parts.length == 3) {
                                try {
                                    String citizenDocument = parts[1];
                                    int candidateId = Integer.parseInt(parts[2]);
                                    VoteData vote = new VoteData(citizenDocument, candidateId, numericTableId, 
                                                                 LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                                    votingTableImpl.emitVote(vote, null); 
                                    System.out.println("Vote for citizen document " + citizenDocument + " to candidate " + candidateId + " submitted.");
                                } catch (NumberFormatException e) {
                                    System.err.println("Invalid candidate ID format. Must be an integer. Citizen document should be a string.");
                                } catch (ElectionInactive e) {
                                    System.err.println("Vote submission failed: " + e.reason);
                                } catch (Exception e) {
                                    System.err.println("Error submitting vote: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                System.err.println("Usage: vote <citizenDocument> <candidateId>");
                            }
                            break;
                        case "candidates":
                            try {
                                CandidateData[] candidates = controlCenterService.getCandidates();
                                if (candidates != null && candidates.length > 0) {
                                    System.out.println("Available Candidates:");
                                    for (CandidateData c : candidates) {
                                        System.out.println("  ID: " + c.id + ", Name: " + c.firstName + " " + c.lastName + ", Party: " + c.party);
                                    }
                                } else {
                                    System.out.println("No candidates available or error fetching them.");
                                }
                            } catch (Exception e) {
                                System.err.println("Error fetching candidates: " + e.getMessage());
                            }
                            break;
                        case "status":
                            System.out.println("Election active at this table: " + votingTableImpl.isElectionActive());
                            break;
                        case "exit":
                            running = false;
                            break;
                        default:
                            if (!command.isEmpty()) {
                                System.err.println("Unknown command: " + command);
                            }
                            System.out.println("Available commands: vote <citizenDocument> <candidateId> | candidates | status | exit");
                            break;
                    }
                } catch (com.zeroc.Ice.CommunicatorDestroyedException e) {
                    System.err.println("Communicator destroyed. Exiting UI loop.");
                    running = false; 
                }
            }
            
            System.out.println("Shutting down VotingTable UI for " + tableIdStr + "...");

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
