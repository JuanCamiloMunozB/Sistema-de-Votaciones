package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "mesa_votacion", indexes = {
    @Index(name = "idx_voting_table_puesto", columnList = "puesto_id"),
    @Index(name = "idx_voting_table_consecutive", columnList = "consecutive"),
    @Index(name = "idx_voting_table_id_puesto", columnList = "id, puesto_id")
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Data
public class VotingTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "consecutive")
    private Integer consecutive;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "puesto_id", nullable = false)
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private VotingStation votingStation;

}