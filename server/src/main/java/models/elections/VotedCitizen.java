package models.elections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "voted_citizens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotedCitizen {

    @Id
    @Column(name = "citizen_id")
    private Integer citizenId;

} 