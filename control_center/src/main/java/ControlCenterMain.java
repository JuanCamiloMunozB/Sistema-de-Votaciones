import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ServerServicePrx;
import java.util.Scanner;

public class ControlCenterMain {
    
    public static void main(String[] args) {
        String controlCenterId = System.getProperty("CONTROL_CENTER_ID", "ControlCenter1");

        try(Communicator communicator = Util.initialize(args, "config.control.cfg");
            Scanner scanner = new Scanner(System.in)) { 
            
            ServerServicePrx serverService = ServerServicePrx.checkedCast(
                communicator.stringToProxy("ServerService"));

            if (serverService == null) {
                System.err.println("Error: Could not get a proxy for ServerService load balancer from IceGrid. Check locator configuration and if ServerService replicas are running and registered.");
                return;
            }
            System.out.println("Successfully obtained ServerServicePrx (load balanced) from IceGrid.");

            ObjectAdapter adapter = communicator.createObjectAdapter("ControlCenterAdapter");
            
            ControlCenterImpl controlCenterImpl = new ControlCenterImpl(serverService, controlCenterId, adapter);

            adapter.add(controlCenterImpl, Util.stringToIdentity("ControlCenterService")); 
            
            adapter.activate();
            System.out.println("ControlCenterService ready and registered with adapter ControlCenterAdapter under identity 'ControlCenterService'.");
            
            controlCenterImpl.initializeSubscription();

            System.out.println("\nControl Center UI (" + controlCenterId + ")");
            System.out.println("Commands: start | end | exit");
            boolean running = true;
            while(running) {
                System.out.print("> ");
                String command = scanner.nextLine().trim().toLowerCase();
                try {
                    switch(command) {
                        case "start":
                            controlCenterImpl.startElection(null);
                            System.out.println("Attempted to start the election.");
                            break;
                        case "end":
                            controlCenterImpl.endElection(null);
                            System.out.println("Attempted to end the election.");
                            break;
                        case "exit":
                            running = false;
                            break;
                        default:
                            if (!command.isEmpty()) {
                                System.err.println("Unknown command: " + command);
                            }
                            System.out.println("Available commands: start | end | exit");
                            break;
                    }
                } catch (com.zeroc.Ice.CommunicatorDestroyedException e) {
                    System.err.println("Communicator destroyed. Exiting UI loop.");
                    running = false; 
                } catch (Exception e) {
                    System.err.println("Error executing command '" + command + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
            System.out.println("Shutting down ControlCenter UI for " + controlCenterId + "...");

        } catch (com.zeroc.Ice.LocalException e) {
            System.err.println("Ice Local Exception in ControlCenterMain: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred in ControlCenterMain: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("ControlCenterMain for '" + controlCenterId + "' shut down.");
    }
}
