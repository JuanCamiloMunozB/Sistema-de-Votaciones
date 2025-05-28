import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import ElectionSystem.ServerServicePrx;

public class ControlCenterMain {
    
    public static void main(String[] args) {
        try(Communicator communicator = Util.initialize(args, "config.control.cfg")) {
            ObjectAdapter adapter = communicator.createObjectAdapter("ControlCenter");
            ServerServicePrx serverService = ServerServicePrx.checkedCast(communicator.propertyToProxy("ServerService.Proxy"));
            adapter.add(new ControlCenterImpl(serverService), Util.stringToIdentity("ServerService"));
            adapter.activate();
            communicator.waitForShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
