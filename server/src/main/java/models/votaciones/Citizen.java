package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "ciudadano", indexes = {
    @Index(name = "idx_citizen_document", columnList = "documento")
})
@Data
public class Citizen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "documento")
    private String document;

    @Column(name = "nombre")
    private String firstName;

    @Column(name = "apellido")
    private String lastName;

    @ManyToOne
    @JoinColumn(name = "mesa_id")
    private VotingTable votingTable;

}