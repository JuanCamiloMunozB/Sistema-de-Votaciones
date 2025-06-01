package repositories.elections;

import models.elections.VotedCitizen;
import repositories.GenericRepository;
import utils.JPAUtil;

public class VotedCitizenRepository extends GenericRepository<VotedCitizen, Integer> {

    public VotedCitizenRepository() {
        super(JPAUtil.getEntityManagerElections(), VotedCitizen.class);
    }

    public boolean existsById(Integer citizenId) {
        return JPAUtil.executeInTransaction(this.entityManager, em -> {
            return em.find(VotedCitizen.class, citizenId) != null;
        });
    }
} 