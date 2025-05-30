package repositories.votaciones;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.TypedQuery;
import models.votaciones.VotingTable;
import repositories.GenericRepository;
import utils.JPAUtil;

public class VotingTableRepository extends GenericRepository<VotingTable, Integer> {

    public VotingTableRepository() {
        super(JPAUtil.getEntityManagerVoting(), VotingTable.class);
    }

    public List<VotingTable> findVotingTablesByVotingStationId(Integer votingStationId) {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String jpql = "SELECT vt FROM VotingTable vt WHERE vt.votingStation.id = :votingStationId";
            return entityManager.createQuery(jpql, VotingTable.class)
                                .setParameter("votingStationId", votingStationId)
                                .getResultList();
        });
    }

    public Map<Integer, List<VotingTable>> groupVotingTablesByStation(){
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            String jpql = "SELECT vt FROM VotingTable vt JOIN FETCH vt.votingStation vs";
            TypedQuery<VotingTable> query = entityManager.createQuery(jpql, VotingTable.class);
            List<VotingTable> tables = query.getResultList();

            if (tables == null) {
                return new java.util.HashMap<>();
            }
            return tables.stream()
                         .collect(Collectors.groupingBy(table -> {
                             if (table.getVotingStation() != null && table.getVotingStation().getId() != null) {
                                 Number stationId = (Number) table.getVotingStation().getId();
                                 return stationId.intValue();
                             } else {
                                 return -1;
                             }
                         }));
        });
    }
    
}
