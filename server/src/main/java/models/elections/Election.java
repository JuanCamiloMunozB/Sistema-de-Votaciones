package models.elections;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import java.time.LocalDateTime;

@Entity
@Table(name = "election", indexes = {
    @Index(name = "idx_election_dates", columnList = "start_date, end_date"), // For finding current elections
    @Index(name = "idx_election_start_date", columnList = "start_date"),
    @Index(name = "idx_election_name", columnList = "name")
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Data
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endTime;
}
