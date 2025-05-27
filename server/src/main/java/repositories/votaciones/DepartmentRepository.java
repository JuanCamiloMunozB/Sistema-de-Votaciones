package repositories.votaciones;

import models.votaciones.Department;
import repositories.GenericRepository;
import utils.JPAUtil;

public class DepartmentRepository extends GenericRepository<Department, Integer> {

    public DepartmentRepository() {
        super(JPAUtil.getEntityManagerVoting(), Department.class);
    }
    
}
