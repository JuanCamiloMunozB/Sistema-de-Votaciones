package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "departamento", indexes = {
    @Index(name = "idx_department_name", columnList = "nombre")
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Data
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre")
    private String name;
    
}