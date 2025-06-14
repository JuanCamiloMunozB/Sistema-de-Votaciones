package models.elections;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "voted_citizens", indexes = {
    @Index(name = "idx_voted_citizen_id", columnList = "citizen_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotedCitizen {

    @Id
    @Column(name = "citizen_id")
    private Integer citizenId;

}