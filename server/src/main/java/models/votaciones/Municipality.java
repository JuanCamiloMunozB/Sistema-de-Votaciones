package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "municipio", indexes = {
    @Index(name = "idx_municipality_departamento", columnList = "departamento_id")
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Data
public class Municipality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre")
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departamento_id")
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private Department department;

}