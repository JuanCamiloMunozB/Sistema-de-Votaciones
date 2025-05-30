package repositories.elections;

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
}
