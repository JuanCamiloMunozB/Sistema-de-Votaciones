package repositories.votaciones;

import models.votaciones.Citizen;
import repositories.GenericRepository;
import utils.JPAUtil;

public class CitizenRepository extends GenericRepository<Citizen, Integer> {

    public CitizenRepository() {
        super(JPAUtil.getEntityManagerVoting(), Citizen.class);
    }
    
}
