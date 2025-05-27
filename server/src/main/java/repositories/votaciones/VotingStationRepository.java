package repositories.votaciones;

import models.votaciones.VotingStation;
import repositories.GenericRepository;
import utils.JPAUtil;

public class VotingStationRepository extends GenericRepository<VotingStation, Integer> {

    public VotingStationRepository() {
        super(JPAUtil.getEntityManagerVoting(), VotingStation.class);
    }
    
}
