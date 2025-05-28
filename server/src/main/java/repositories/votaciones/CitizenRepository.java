package repositories.votaciones;

import models.votaciones.Citizen;
import repositories.GenericRepository;
import utils.JPAUtil;

import java.util.Optional;

public class CitizenRepository extends GenericRepository<Citizen, Integer> {

    public CitizenRepository() {
        super(JPAUtil.getEntityManagerVoting(), Citizen.class);
    }

    public Optional<Citizen> findByDocument(String document) {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String jpql = "SELECT c FROM Citizen c WHERE c.document = :document";
            try {
                Citizen citizen = entityManager.createQuery(jpql, Citizen.class)
                                                .setParameter("document", document)
                                                .getSingleResult();
                return Optional.of(citizen);
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }
    
}
