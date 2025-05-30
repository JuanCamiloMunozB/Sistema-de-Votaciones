create user electuser with password '123456';
create database votaciones owner electuser;
grant connect on database votaciones to electuser;

create database elections owner electuser;
grant connect on database elections to electuser;

CREATE TABLE department (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE municipality (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    department_id INT NOT NULL,
    CONSTRAINT fk_municipality_department FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE voting_station (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    municipality_id INT NOT NULL,
    CONSTRAINT fk_voting_station_municipality FOREIGN KEY (municipality_id) REFERENCES municipality(id)
);

CREATE TABLE voting_table (
    id SERIAL PRIMARY KEY, 
    code VARCHAR(255) UNIQUE, -- Ejemplo: "M001", "M002", etc. O podría ser solo un número.
                             -- Revisa el campo correspondiente en tu entidad VotingTable.java
    voting_station_id INT NOT NULL,
    CONSTRAINT fk_voting_table_station FOREIGN KEY (voting_station_id) REFERENCES voting_station(id) 
);

CREATE TABLE citizen (
    id SERIAL PRIMARY KEY,
    document VARCHAR(255) NOT NULL UNIQUE, -- "documento" en tu descripción
    first_name VARCHAR(255) NOT NULL,     -- "nombre"
    last_name VARCHAR(255) NOT NULL,      -- "apellido"
    -- Considera añadir:
    -- middle_name VARCHAR(255),
    -- second_last_name VARCHAR(255),
    voting_table_id INT, -- "mesa_id" en tu descripción. Esta es la FK.
    CONSTRAINT fk_citizen_voting_table FOREIGN KEY (voting_table_id) REFERENCES voting_table(id)
);

-- Permisos en la BD 'votaciones'
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE department, municipality, voting_station, voting_table, citizen TO electuser;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO electuser;

CREATE TABLE election (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date TIMESTAMP NOT NULL, -- O DATE, o VARCHAR si así lo manejas
    end_date TIMESTAMP NOT NULL   -- O DATE, o VARCHAR
);

CREATE TABLE candidate (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    party VARCHAR(255),
    election_id INT NOT NULL,
    CONSTRAINT fk_candidate_election FOREIGN KEY (election_id) REFERENCES election(id)
);

CREATE TABLE votes (
    -- 'id' para esta tabla de votos, o citizen_id puede ser PK si un ciudadano solo vota una vez en general.
    -- Si citizen_id es PK, no puede haber múltiples votos del mismo ciudadano en diferentes elecciones.
    -- Por eso, un ID autoincremental para la tabla 'votes' suele ser mejor.
    id SERIAL PRIMARY KEY, 
    citizen_id INT NOT NULL,    -- Este es el ID del ciudadano de la BD 'votaciones'. 
                                -- No es una FK directa entre BDs. Se valida en la lógica de aplicación.
    candidate_id INT NOT NULL,
    election_id INT NOT NULL,   -- Para saber a qué elección pertenece este voto
    table_id INT NOT NULL,      -- Este es el ID de la mesa de la BD 'votaciones'.
                                -- Se valida en la lógica de aplicación.
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (citizen_id, election_id), -- Un ciudadano solo puede votar una vez por elección
    CONSTRAINT fk_vote_candidate FOREIGN KEY (candidate_id) REFERENCES candidate(id),
    CONSTRAINT fk_vote_election FOREIGN KEY (election_id) REFERENCES election(id)
);

INSERT INTO election (name, start_date, end_date) 
VALUES ('Elección Municipal 2025', '2025-01-01 08:00:00', '2025-12-31 17:00:00');

-- Permisos en la BD 'elections'
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE election, candidate, votes TO electuser;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO electuser;