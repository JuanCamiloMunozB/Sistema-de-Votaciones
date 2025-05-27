package repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class GenericRepository<T, ID> {

    protected final EntityManager entityManager;
    private final Class<T> entityClass;

    public GenericRepository(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    public T findById(ID id) {
        return entityManager.find(entityClass, id);
    }

    public List<T> findAll() {
        String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        return query.getResultList();
    }

    public void save(T entity) {
        utils.JPAUtil.executeInTransactionVoid(entityManager, em -> em.persist(entity));
    }

    public void update(T entity) {
        utils.JPAUtil.executeInTransactionVoid(entityManager, em -> em.merge(entity));
    }

    public void delete(T entity) {
        utils.JPAUtil.executeInTransactionVoid(entityManager, em -> {
            T managed = em.contains(entity) ? entity : em.merge(entity);
            em.remove(managed);
        });
    }
}
