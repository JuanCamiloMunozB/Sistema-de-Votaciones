package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "puesto_votacion", indexes = {
    @Index(name = "idx_voting_station_municipio", columnList = "municipio_id"),
    @Index(name = "idx_voting_station_consecutive", columnList = "consecutive")
})
@Data
public class VotingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre")
    private String name;

    @Column(name = "consecutive")
    private Integer consecutive;

    @Column(name = "direccion")
    private String address;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "municipio_id")
    private Municipality municipality;

}