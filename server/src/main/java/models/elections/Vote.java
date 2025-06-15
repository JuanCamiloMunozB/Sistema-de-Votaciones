package models.elections;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import java.time.LocalDateTime;

@Entity
@Table(name = "votes", indexes = {
    @Index(name = "idx_vote_candidate", columnList = "candidate_id"),
    @Index(name = "idx_vote_election", columnList = "election_id"),
    @Index(name = "idx_vote_table", columnList = "table_id"),
    @Index(name = "idx_vote_timestamp", columnList = "timestamp"),
    @Index(name = "idx_vote_candidate_table", columnList = "candidate_id, table_id"), // For results by table
    @Index(name = "idx_vote_election_timestamp", columnList = "election_id, timestamp"), // For temporal queries
    @Index(name = "idx_vote_candidate_election", columnList = "candidate_id, election_id"), // For global results
    @Index(name = "idx_vote_table_timestamp", columnList = "table_id, timestamp") // For table activity tracking
})
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Data
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY) // Changed to LAZY for better performance
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY) // Changed to LAZY for better performance
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "table_id", nullable = false)
    private Integer tableId;

}
