package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "puesto_votacion")
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

    @ManyToOne
    @JoinColumn(name = "municipio_id")
    private Municipality municipality;

}
