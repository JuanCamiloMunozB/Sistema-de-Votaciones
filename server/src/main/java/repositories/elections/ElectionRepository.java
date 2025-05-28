package repositories.elections;

import models.elections.Election;
import repositories.GenericRepository;
import utils.JPAUtil;

public class ElectionRepository extends GenericRepository<Election, Integer> {

    public ElectionRepository() {
        super(JPAUtil.getEntityManagerElections(), Election.class);
    }
    
}
