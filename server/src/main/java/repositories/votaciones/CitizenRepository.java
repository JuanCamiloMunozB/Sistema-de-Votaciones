package repositories.votaciones;

import models.votaciones.Citizen;
import models.votaciones.VotingTable;
import utils.JPAUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CitizenRepository {
    
    private static final Map<String, CitizenValidationData> validationCache = new ConcurrentHashMap<>();
    private static final long VALIDATION_CACHE_TTL = 60000;
    private static final Map<String, Citizen> documentCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 300000;
    private static volatile long lastCacheUpdate = 0;

    public static class CitizenValidationData {
        public final Integer citizenId;
        public final Integer tableId;
        public final long cacheTime;
        
        public CitizenValidationData(Integer citizenId, Integer tableId) {
            this.citizenId = citizenId;
            this.tableId = tableId;
            this.cacheTime = System.currentTimeMillis();
        }
        
        public boolean isValid() {
            return System.currentTimeMillis() - cacheTime < VALIDATION_CACHE_TTL;
        }
    }
    
    public CitizenValidationData getCitizenValidationData(String document) {
        CitizenValidationData cached = validationCache.get(document);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        
        EntityManager entityManager = JPAUtil.getEntityManagerVoting();
        return JPAUtil.executeInTransaction(entityManager, em -> {
            try {
                TypedQuery<Object[]> query = em.createQuery(
                    "SELECT c.id, c.votingTable.id FROM Citizen c WHERE c.document = :document", 
                    Object[].class
                );
                query.setParameter("document", document);
                query.setHint("org.hibernate.readOnly", true);
                query.setHint("org.hibernate.cacheable", true);
                
                Object[] result = query.getSingleResult();
                CitizenValidationData data = new CitizenValidationData(
                    (Integer) result[0], 
                    (Integer) result[1]
                );
                
                if (validationCache.size() < 10000) {
                    validationCache.put(document, data);
                }
                
                return data;
            } catch (NoResultException e) {
                return null;
            }
        });
    }

    public Optional<Citizen> findByDocument(String document) {
        Citizen cachedCitizen = getCachedCitizen(document);
        if (cachedCitizen != null) {
            return Optional.of(cachedCitizen);
        }
        
        EntityManager entityManager = JPAUtil.getEntityManagerVoting();
        return JPAUtil.executeInTransaction(entityManager, em -> {
            try {
                TypedQuery<Citizen> query = em.createQuery(
                    "SELECT c FROM Citizen c " +
                    "JOIN FETCH c.votingTable vt " +
                    "JOIN FETCH vt.votingStation vs " +
                    "JOIN FETCH vs.municipality m " +
                    "JOIN FETCH m.department d " +
                    "WHERE c.document = :document", 
                    Citizen.class
                );
                query.setParameter("document", document);
                
                query.setHint("org.hibernate.cacheable", true);
                query.setHint("org.hibernate.readOnly", true);
                query.setHint("org.hibernate.fetchSize", 1);
                
                Citizen citizen = query.getSingleResult();
                
                cacheCitizen(document, citizen);
                
                return Optional.of(citizen);
            } catch (NoResultException e) {
                return Optional.empty();
            } catch (Exception e) {
                System.err.println("Error finding citizen by document: " + e.getMessage());
                return Optional.empty();
            }
        });
    }
    
    private Citizen getCachedCitizen(String document) {
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_TTL) {
            documentCache.clear();
            lastCacheUpdate = System.currentTimeMillis();
            return null;
        }
        return documentCache.get(document);
    }
    
    private void cacheCitizen(String document, Citizen citizen) {
        if (documentCache.size() < 1000) {
            documentCache.put(document, citizen);
        }
    }

    public List<Citizen> findByVotingTableId(Integer tableId) {
        EntityManager entityManager = JPAUtil.getEntityManagerVoting();
        return JPAUtil.executeInTransaction(entityManager, em -> {
            TypedQuery<Citizen> query = em.createQuery(
                "SELECT c FROM Citizen c WHERE c.votingTable.id = :tableId", 
                Citizen.class
            );
            query.setParameter("tableId", tableId);
            query.setHint("org.hibernate.cacheable", true);
            query.setHint("org.hibernate.readOnly", true);
            return query.getResultList();
        });
    }

    public Map<VotingTable, List<Citizen>> groupCitizensByVotingTable() {
        EntityManager entityManager = JPAUtil.getEntityManagerVoting();
        return JPAUtil.executeInTransaction(entityManager, em -> {
            try {
                TypedQuery<Citizen> query = em.createQuery(
                    "SELECT c FROM Citizen c " +
                    "JOIN FETCH c.votingTable vt " +
                    "ORDER BY vt.id", 
                    Citizen.class
                );
                
                List<Citizen> allCitizens = query.getResultList();
                
                return allCitizens.stream()
                    .collect(Collectors.groupingBy(Citizen::getVotingTable));
                    
            } catch (Exception e) {
                System.err.println("Error grouping citizens by voting table: " + e.getMessage());
                return Map.of();
            }
        });
    }

    public Integer findVotingTableIdByDocument(String document) {
        return JPAUtil.executeReadOnlyQuery(em -> {
            try {
                jakarta.persistence.Query query = em.createNativeQuery(
                    "SELECT mesa_id FROM ciudadano WHERE documento = ? LIMIT 1"
                );
                query.setParameter(1, document);
                
                query.setHint("org.hibernate.readOnly", true);
                query.setHint("org.hibernate.cacheable", false);
                query.setHint("org.hibernate.timeout", 50);
                query.setHint("org.hibernate.fetchSize", 1);
                
                List<Object> results = query.getResultList();
                
                if (!results.isEmpty() && results.get(0) != null) {
                    Object result = results.get(0);
                    if (result instanceof Number) {
                        return ((Number) result).intValue();
                    } else if (result instanceof String) {
                        return Integer.parseInt((String) result);
                    }
                }
                return null;
                
            } catch (Exception e) {
                System.err.println("Error in native query for document " + document + ": " + e.getMessage());
                return null;
            }
        });
    }
}