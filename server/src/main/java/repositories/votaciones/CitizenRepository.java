package repositories.votaciones;

import models.votaciones.Citizen;
import repositories.GenericRepository;
import utils.JPAUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.persistence.TypedQuery;

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
            // Usar JPQL para obtener los Citizen con sus VotingTable asociadas
            String jpql = "SELECT c FROM Citizen c LEFT JOIN FETCH c.votingTable vt"; // LEFT JOIN FETCH si un ciudadano puede no tener mesa
                                                                              // o JOIN FETCH si siempre tiene mesa.
            TypedQuery<Citizen> query = entityManager.createQuery(jpql, Citizen.class);
            List<Citizen> citizens = query.getResultList();

            // Agrupar en Java
            if (citizens == null) {
                return new java.util.HashMap<>();
            }
            return citizens.stream()
                .filter(citizen -> citizen.getVotingTable() != null && citizen.getVotingTable().getId() != null) // Solo agrupar si tiene mesa y la mesa tiene ID
                .collect(Collectors.groupingBy(citizen -> {
                    // Asumiendo que el ID de VotingTable es Long o Integer. Ajusta según tu entidad.
                    // Si el ID de VotingTable es Long, el Map debe ser Map<Long, List<Citizen>>
                    // y ServerImpl.java debe ajustarse para manejar esto o convertir las claves.
                    // Aquí, para mantener la clave Integer:
                    Number tableId = (Number) citizen.getVotingTable().getId();
                    return tableId.intValue();
                }));
        });
    }
    
}
