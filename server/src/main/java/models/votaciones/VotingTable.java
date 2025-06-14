package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "mesa_votacion", indexes = {
    @Index(name = "idx_voting_table_puesto", columnList = "puesto_id"),
    @Index(name = "idx_voting_table_consecutive", columnList = "consecutive")
})
@Data
public class VotingTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "consecutive")
    private Integer consecutive;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "puesto_id", nullable = false)
    private VotingStation votingStation;

}