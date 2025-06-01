package repositories.votaciones;

import models.votaciones.Citizen;
import models.votaciones.VotingTable;
import repositories.GenericRepository;
import utils.JPAUtil;

import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CitizenRepository extends GenericRepository<Citizen, Integer> {

    private static final int PAGE_SIZE = 1000;

    public CitizenRepository() {
        super(JPAUtil.getEntityManagerVoting(), Citizen.class);
    }

    public Optional<Citizen> findByDocument(String document) {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            TypedQuery<Citizen> query = entityManager.createQuery(
                "SELECT c FROM Citizen c WHERE c.document = :document", Citizen.class);
            query.setParameter("document", document);
            return query.getResultStream().findFirst();
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

    public Map<VotingTable, List<Citizen>> groupCitizensByVotingTable() {
        return JPAUtil.executeInTransaction(this.entityManager, entityManager -> {
            Map<VotingTable, List<Citizen>> citizensByTable = new HashMap<>();

            TypedQuery<VotingTable> tablesQuery = entityManager.createQuery("SELECT vt FROM VotingTable vt", VotingTable.class);
            List<VotingTable> allVotingTables = tablesQuery.getResultList();
            System.out.println("[CitizenRepository] Total voting tables found: " + allVotingTables.size());

            for (VotingTable table : allVotingTables) {
                System.out.println("[CitizenRepository] Processing citizens for table ID: " + table.getId() + ", Consecutive: " + table.getConsecutive());
                List<Citizen> citizensForCurrentTable = new ArrayList<>();
                int pageNumber = 0;
                List<Citizen> pageResults;

                do {
                    TypedQuery<Citizen> citizenQuery = entityManager.createQuery(
                        "SELECT c FROM Citizen c WHERE c.votingTable = :table", Citizen.class);
                    citizenQuery.setParameter("table", table);
                    citizenQuery.setFirstResult(pageNumber * PAGE_SIZE);
                    citizenQuery.setMaxResults(PAGE_SIZE);
                    pageResults = citizenQuery.getResultList();

                    citizensForCurrentTable.addAll(pageResults);
                    pageNumber++;
                    System.out.println("[CitizenRepository] Loaded " + pageResults.size() + " citizens for table ID " + table.getId() + " (page " + (pageNumber-1) + ")");
                } while (!pageResults.isEmpty() && pageResults.size() == PAGE_SIZE);
                if (!citizensForCurrentTable.isEmpty()) {
                    citizensByTable.put(table, citizensForCurrentTable);
                    System.out.println("[CitizenRepository] Total citizens for table ID " + table.getId() + ": " + citizensForCurrentTable.size());
                } else {
                    System.out.println("[CitizenRepository] No citizens found for table ID " + table.getId());
                }
            }
            System.out.println("[CitizenRepository] Finished grouping citizens. Tables with citizens: " + citizensByTable.size());
            return citizensByTable;
        });
    }
    
}
