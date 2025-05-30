package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "voting_table")
@Data
public class VotingTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code")
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voting_station_id", nullable = false)
    private VotingStation votingStation;

    public VotingStation getVotingStation() {
        return this.votingStation;
    }
}
