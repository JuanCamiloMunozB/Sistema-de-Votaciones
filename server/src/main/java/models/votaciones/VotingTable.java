package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "mesa_votacion")
@Data
public class VotingTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "consecutive")
    private Integer consecutive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puesto_id", nullable = false)
    private VotingStation votingStation;

}