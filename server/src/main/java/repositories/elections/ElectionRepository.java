package repositories.elections;

import java.util.Optional;

import models.elections.Election;
import repositories.GenericRepository;
import utils.JPAUtil;

public class ElectionRepository extends GenericRepository<Election, Integer> {

    public ElectionRepository() {
        super(JPAUtil.getEntityManagerElections(), Election.class);
    }
    
    public Optional<Election> findCurrentElection() {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String jpql = "SELECT e FROM Election e WHERE e.startTime <= CURRENT_TIMESTAMP AND e.endTime >= CURRENT_TIMESTAMP";
            try {
                Election election = entityManager.createQuery(jpql, Election.class)
                                                  .getSingleResult();
                return Optional.of(election);
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }
}
