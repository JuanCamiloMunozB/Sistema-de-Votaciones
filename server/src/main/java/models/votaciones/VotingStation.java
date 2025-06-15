package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "puesto_votacion", indexes = {
    @Index(name = "idx_voting_station_municipio", columnList = "municipio_id"),
    @Index(name = "idx_voting_station_consecutive", columnList = "consecutive"),
    @Index(name = "idx_voting_station_name", columnList = "nombre"),
    @Index(name = "idx_voting_station_municipio_name", columnList = "municipio_id, nombre")
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
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
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private Municipality municipality;

}