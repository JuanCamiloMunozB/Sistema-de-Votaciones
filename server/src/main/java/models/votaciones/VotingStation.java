package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "voting_station")
@Data
public class VotingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "address")
    private String address;

    @ManyToOne
    @JoinColumn(name = "municipality_id")
    private Municipality municipality;

}
