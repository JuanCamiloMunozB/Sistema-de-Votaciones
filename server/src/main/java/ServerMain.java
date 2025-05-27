import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import utils.JPAUtil;

public class ServerMain {
    
    public static void main(String[] args) {
        try(Communicator communicator = Util.initialize(args, "config.server.cfg")) {
            JPAUtil.initialize(communicator);
            ObjectAdapter adapter = communicator.createObjectAdapter("Server");
            adapter.add(new ServerImpl(), Util.stringToIdentity("ServerService"));
            adapter.activate();
            communicator.waitForShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}