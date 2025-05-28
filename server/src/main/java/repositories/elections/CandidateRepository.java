package repositories.elections;

import java.util.List;

import models.elections.Candidate;
import repositories.GenericRepository;
import utils.JPAUtil;

public class CandidateRepository extends GenericRepository<Candidate, Integer> {

    public CandidateRepository() {
        super(JPAUtil.getEntityManagerElections(), Candidate.class);
    }

    public List<Candidate> findCandidatesByElectionId(Integer electionId) {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String jpql = "SELECT c FROM Candidate c WHERE c.election.id = :electionId";
            return entityManager.createQuery(jpql, Candidate.class)
                                .setParameter("electionId", electionId)
                                .getResultList();
        });
    }
    
}
