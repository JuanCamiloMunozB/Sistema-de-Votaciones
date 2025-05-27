package repositories.votaciones;

import models.votaciones.VotingTable;
import repositories.GenericRepository;
import utils.JPAUtil;

public class VotingTableRepository extends GenericRepository<VotingTable, Integer> {

    public VotingTableRepository() {
        super(JPAUtil.getEntityManagerVoting(), VotingTable.class);
    }
    
}
