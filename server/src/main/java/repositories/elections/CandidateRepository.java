package repositories.elections;

import models.elections.Candidate;
import repositories.GenericRepository;
import utils.JPAUtil;

public class CandidateRepository extends GenericRepository<Candidate, Integer> {

    public CandidateRepository() {
        super(JPAUtil.getEntityManagerElections(), Candidate.class);
    }
    
}
