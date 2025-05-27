package repositories.votaciones;

import models.votaciones.Municipality;
import repositories.GenericRepository;
import utils.JPAUtil;

public class MunicipalityRepository extends GenericRepository<Municipality, Integer> {

    public MunicipalityRepository() {
        super(JPAUtil.getEntityManagerVoting(), Municipality.class);
    }
    
}
