package models.votaciones;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "ciudadano", indexes = {
    @Index(name = "idx_citizen_document", columnList = "documento", unique = true),
    @Index(name = "idx_citizen_mesa_id", columnList = "mesa_id"),
    @Index(name = "idx_citizen_document_mesa", columnList = "documento, mesa_id"),
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Data
public class Citizen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "documento", nullable = false, unique = true)
    private String document;

    @Column(name = "nombre")
    private String firstName;

    @Column(name = "apellido")
    private String lastName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mesa_id")
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private VotingTable votingTable;

}