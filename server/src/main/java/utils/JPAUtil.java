package utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Properties;

import java.util.HashMap;
import java.util.Map;

public class JPAUtil {

    private static EntityManagerFactory emfVoting;
    private static EntityManagerFactory emfElections;
    private static final Object lock = new Object();
    private static Communicator communicator;

    public static void initialize(Communicator communicator) {
        JPAUtil.communicator = communicator;

        synchronized (lock) {
            if (emfVoting == null || emfElections == null) {
                createEntityManagerFactories();
            }
        }
    }

    private static void createEntityManagerFactories() {
        try {
            // Common properties
            Map<String, String> baseProperties = new HashMap<>();
            baseProperties.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
            baseProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            baseProperties.put("hibernate.show_sql", "false");
            baseProperties.put("hibernate.format_sql", "true");
            baseProperties.put("hibernate.connection.pool_size", "10");

            Properties config = communicator.getProperties();

            // EntityManagerFactory for votaciones
            Map<String, String> votingProps = new HashMap<>(baseProperties);
            votingProps.put("hibernate.hbm2ddl.auto", "validate");
            votingProps.put("jakarta.persistence.jdbc.user", config.getProperty("database.votaciones.user"));
            votingProps.put("jakarta.persistence.jdbc.password", config.getProperty("database.votaciones.password"));
            votingProps.put("jakarta.persistence.jdbc.url", config.getProperty("database.votaciones.url"));
            System.out.println(votingProps);
            emfVoting = Persistence.createEntityManagerFactory("VotingPU", votingProps);

            // EntityManagerFactory for Application DB (votos, candidatos, elecciones, etc)
            Map<String, String> electionProps = new HashMap<>(baseProperties);
            electionProps.put("hibernate.hbm2ddl.auto", "create-drop");
            electionProps.put("jakarta.persistence.jdbc.user", config.getProperty("database.elections.user"));
            electionProps.put("jakarta.persistence.jdbc.password", config.getProperty("database.elections.password"));
            electionProps.put("jakarta.persistence.jdbc.url", config.getProperty("database.elections.url"));
            emfElections = Persistence.createEntityManagerFactory("ElectionPU", electionProps);

            System.out.println("EntityManagerFactories initialized successfully");

        } catch (Exception e) {
            System.err.println("Error initializing JPA: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("JPA initialization failed", e);
        }
    }

    public static EntityManager getEntityManagerVoting() {
        if (emfVoting == null) {
            throw new IllegalStateException("JPAUtil has not been initialized. Call initialize() first.");
        }
        return emfVoting.createEntityManager();
    }

    public static EntityManager getEntityManagerElections() {
        if (emfElections == null) {
            throw new IllegalStateException("JPAUtil has not been initialized. Call initialize() first.");
        }
        return emfElections.createEntityManager();
    }

    public static void shutdown() {
        synchronized (lock) {
            if (emfVoting != null && emfVoting.isOpen()) {
                emfVoting.close();
                System.out.println("Voting EntityManagerFactory closed");
            }
            if (emfElections != null && emfElections.isOpen()) {
                emfElections.close();
                System.out.println("Elections EntityManagerFactory closed");
            }
        }
    }

    public static <T> T executeInTransaction(EntityManager em, TransactionCallback<T> callback) {
        em.getTransaction().begin();
        try {
            T result = callback.execute(em);
            em.getTransaction().commit();
            return result;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("Error in transaction: " + e.getMessage(), e);
        }
    }

    public static void executeInTransactionVoid(EntityManager em, TransactionCallbackVoid callback) {
        em.getTransaction().begin();
        try {
            callback.execute(em);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("Error in transaction: " + e.getMessage(), e);
        }
    }

    // Interfaces for transaction callbacks
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(EntityManager em) throws Exception;
    }

    @FunctionalInterface
    public interface TransactionCallbackVoid {
        void execute(EntityManager em) throws Exception;
    }
}