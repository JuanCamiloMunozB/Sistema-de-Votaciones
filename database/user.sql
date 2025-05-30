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

-- Insert test election data
INSERT INTO elections (id, name, start_time, end_time, created_at, updated_at) VALUES 
(1, 'Elección Municipal 2025', '2025-01-01 08:00:00', '2025-12-31 18:00:00', NOW(), NOW());

-- Insert test candidates
INSERT INTO candidates (id, first_name, last_name, party, election_id, created_at, updated_at) VALUES 
(101, 'Ana', 'García', 'Partido Liberal', 1, NOW(), NOW()),
(102, 'Carlos', 'López', 'Partido Conservador', 1, NOW(), NOW()),
(103, 'María', 'Rodríguez', 'Partido Verde', 1, NOW(), NOW()),
(104, 'José', 'Martínez', 'Partido Social', 1, NOW(), NOW());

-- Insert test stations (control centers)
INSERT INTO stations (id, name, address, created_at, updated_at) VALUES 
(1, 'Estación Central', 'Calle Principal 123', NOW(), NOW()),
(2, 'Estación Norte', 'Avenida Norte 456', NOW(), NOW());

-- Insert test voting tables
INSERT INTO voting_tables (id, station_id, created_at, updated_at) VALUES 
(201, 1, NOW(), NOW()),
(202, 1, NOW(), NOW()),
(203, 2, NOW(), NOW());

-- Insert test citizens
INSERT INTO citizens (id, document, first_name, last_name, voting_table_id, created_at, updated_at) VALUES 
(1, '12345678', 'Juan', 'Pérez', 201, NOW(), NOW()),
(2, '87654321', 'Laura', 'González', 201, NOW(), NOW()),
(3, '11223344', 'Pedro', 'Sánchez', 201, NOW(), NOW()),
(4, '44332211', 'Sofía', 'Ruiz', 202, NOW(), NOW()),
(5, '55667788', 'Miguel', 'Torres', 202, NOW(), NOW()),
(6, '88776655', 'Carmen', 'Vargas', 203, NOW(), NOW());

-- Insert initial vote count (optional, for testing)
-- Note: Don't insert actual votes here as they should be created through the voting process
