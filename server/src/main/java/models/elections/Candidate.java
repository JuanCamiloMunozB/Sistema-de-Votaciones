package models.elections;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "candidate", indexes = {
    @Index(name = "idx_candidate_election", columnList = "election_id"),
    @Index(name = "idx_candidate_election_id", columnList = "election_id, id"), // Composite for fast lookups
    @Index(name = "idx_candidate_party", columnList = "party"), // For filtering by party
    @Index(name = "idx_candidate_name", columnList = "first_name, last_name") // For name searches
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Data
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "party")
    private String party;
    
    @ManyToOne(fetch = FetchType.LAZY) // Changed to LAZY for better performance
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;
}
