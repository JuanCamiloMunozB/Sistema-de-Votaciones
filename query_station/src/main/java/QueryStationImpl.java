import ElectionSystem.*;
import com.zeroc.Ice.Current;

public class QueryStationImpl implements queryStation {
    
    private final ServerQueryServicePrx proxyCacheService;
    
    public QueryStationImpl(ServerQueryServicePrx proxyCacheService) {
        this.proxyCacheService = proxyCacheService;
        System.out.println("QueryStationImpl initialized with proxy cache service");
    }
    
    @Override
    public String query(String document, Current current) {
        if (document == null || document.trim().isEmpty()) {
            return null;
        }
        
        try {
            return proxyCacheService.findVotingStationByDocument(document);
        } catch (Exception e) {
            return null;
        }
    }
    
    public void shutdown() {
        System.out.println("QueryStationImpl shutdown completed");
    }
}