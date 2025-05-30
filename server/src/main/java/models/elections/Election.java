package models.elections;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "election")
@Data
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "start_date")
    private LocalDateTime startTime;

    @Column(name = "end_date")
    private LocalDateTime endTime;
}
