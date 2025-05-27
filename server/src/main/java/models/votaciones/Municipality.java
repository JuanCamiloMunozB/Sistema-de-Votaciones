package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "municipio")
@Data
public class Municipality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre")
    private String name;

    @ManyToOne
    @JoinColumn(name = "departamento_id")
    private Department department;

}
