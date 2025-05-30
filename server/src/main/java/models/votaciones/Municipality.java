package models.votaciones;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "municipality")
@Data
public class Municipality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

}
