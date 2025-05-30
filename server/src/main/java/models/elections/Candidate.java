package models.elections;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "candidate")
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
    
    @ManyToOne
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;
}
