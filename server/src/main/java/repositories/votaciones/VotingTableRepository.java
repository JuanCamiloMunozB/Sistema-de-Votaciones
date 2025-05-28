package repositories.votaciones;

import java.util.List;
import java.util.Map;

import models.votaciones.VotingTable;
import repositories.GenericRepository;
import utils.JPAUtil;
import java.util.stream.Collectors;

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
            String sql = "SELECT vt.*, vs.id AS station_id FROM VotingTable vt JOIN VotingStation vs ON vt.votingStation_id = vs.id";
            @SuppressWarnings("unchecked")
            List<Object[]> results = entityManager.createNativeQuery(sql, "VotingTableWithStationIdMapping").getResultList();
            return results.stream()
                          .collect(Collectors.groupingBy(result -> (Integer) result[1],
                                 Collectors.mapping(result -> (VotingTable) result[0], Collectors.toList())));
        });
    }
    
}
