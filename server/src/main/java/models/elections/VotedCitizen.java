package models.elections;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "voted_citizens", indexes = {
    @Index(name = "idx_voted_citizen_id", columnList = "citizen_id", unique = true),
    @Index(name = "idx_voted_citizen_hash", columnList = "citizen_id") // Hash index for faster lookups
})
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE) // Cache for voted status checks
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotedCitizen {

    @Id
    @Column(name = "citizen_id")
    private Integer citizenId;

}