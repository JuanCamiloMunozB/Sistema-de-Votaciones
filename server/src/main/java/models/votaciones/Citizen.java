package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "citizen")
@Data
public class Citizen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "document")
    private String document;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @ManyToOne
    @JoinColumn(name = "voting_table_id")
    private VotingTable votingTable;

}
