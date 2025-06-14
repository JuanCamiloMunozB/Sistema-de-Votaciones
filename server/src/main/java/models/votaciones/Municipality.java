package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "municipio", indexes = {
    @Index(name = "idx_municipality_departamento", columnList = "departamento_id")
})
@Data
public class Municipality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre")
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departamento_id")
    private Department department;

}