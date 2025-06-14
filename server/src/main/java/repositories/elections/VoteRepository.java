package repositories.elections;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.elections.Vote;
import repositories.GenericRepository;
import utils.JPAUtil;

public class VoteRepository extends GenericRepository<Vote, Integer> {

    public VoteRepository() {
        super(JPAUtil.getEntityManagerElections(), Vote.class);
    }

    public boolean hasVoted(int citizenId) {
        return JPAUtil.executeInTransaction(this.entityManager, em -> {
            Long count = em.createQuery(
                "SELECT COUNT(v) FROM Vote v WHERE v.citizenId = :citizenId", 
                Long.class)
                .setParameter("citizenId", citizenId)
                .getSingleResult();
            return count > 0;
        });
    }

    public boolean hasVotedInElection(String citizenDocument, int electionId) {
        return JPAUtil.executeInTransaction(this.entityManager, em -> {
            Long count = em.createQuery(
                "SELECT COUNT(v) FROM Vote v JOIN v.candidate c " +
                "WHERE c.election.id = :electionId AND v.tableCode IN " +
                "(SELECT CAST(vt.consecutive AS string) FROM VotingTable vt JOIN vt.station.municipality.department d " +
                "WHERE vt.id IN (SELECT c2.votingTable.id FROM Citizen c2 WHERE c2.document = :document))", 
                Long.class)
                .setParameter("electionId", electionId)
                .setParameter("document", citizenDocument)
                .getSingleResult();
            
            return count > 0;
        });
    }

    public long countByElectionId(int electionId) {
        return JPAUtil.executeInTransaction(this.entityManager, em -> 
            em.createQuery(
                "SELECT COUNT(v) FROM Vote v JOIN v.candidate c WHERE c.election.id = :electionId", 
                Long.class)
                .setParameter("electionId", electionId)
                .getSingleResult()
        );
    }

    public long countByCandidateId(int candidateId) {
        return JPAUtil.executeInTransaction(this.entityManager, em -> 
            em.createQuery(
                "SELECT COUNT(v) FROM Vote v WHERE v.candidate.id = :candidateId", 
                Long.class)
                .setParameter("candidateId", candidateId)
                .getSingleResult()
        );
    }

    public Map<Integer, Map<Integer, Integer>> countVotesGroupedByTableAndCandidate() {
    return JPAUtil.executeInTransaction(this.entityManager, em -> {
        List<Object[]> results = em.createQuery(
            "SELECT v.tableId, v.candidate.id, COUNT(v) FROM Vote v GROUP BY v.tableId, v.candidate.id"
        ).getResultList();

        Map<Integer, Map<Integer, Integer>> tableToCandidateVotes = new HashMap<>();
        for (Object[] row : results) {
            int tableId = (int) row[0];
            int candidateId = (int) row[1];
            long count = (long) row[2];

            tableToCandidateVotes
                .computeIfAbsent(tableId, k -> new HashMap<>())
                .put(candidateId, (int) count);
        }
        return tableToCandidateVotes;
    });
    }

}
