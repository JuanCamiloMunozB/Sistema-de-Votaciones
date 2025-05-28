package repositories.votaciones;

import models.votaciones.Citizen;
import repositories.GenericRepository;
import utils.JPAUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public List<Citizen> findByVotingTableId(Integer votingTableId) {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String jpql = "SELECT c FROM Citizen c WHERE c.votingTable.id = :votingTableId";
            return entityManager.createQuery(jpql, Citizen.class)
                                .setParameter("votingTableId", votingTableId)
                                .getResultList();
        });
    }

    public Map<Integer, List<Citizen>> groupCitizensByVotingTable() {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String sql = "SELECT c.*, vt.id AS table_id FROM Citizen c JOIN VotingTable vt ON c.votingTable_id = vt.id";
            @SuppressWarnings("unchecked")
            List<Object[]> results = entityManager.createNativeQuery(sql, "CitizenWithTableIdMapping").getResultList();
            return results.stream()
                          .collect(Collectors.groupingBy(result -> (Integer) result[1],
                                  Collectors.mapping(result -> (Citizen) result[0], Collectors.toList())));
        });
    }
    
}
