create user electuser with password '123456';
create database votaciones owner electuser; -- Esta base de datos se carga a partir de un backup
grant connect on database votaciones to electuser;

create database elections owner electuser;
grant connect on database elections to electuser;

-- crear estos esquemas en la base de datos 'elections' utilizando el usuario 'electuser'
CREATE TABLE election (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL  
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
    id SERIAL PRIMARY KEY, 
    candidate_id INT NOT NULL,
    election_id INT NOT NULL,
    table_id INT NOT NULL,      
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vote_candidate FOREIGN KEY (candidate_id) REFERENCES candidate(id),
    CONSTRAINT fk_vote_election FOREIGN KEY (election_id) REFERENCES election(id)
);

CREATE TABLE voted_citizens (
    citizen_id SERIAL PRIMARY KEY
);

INSERT INTO election (id, name, start_date, end_date) 
VALUES (1, 'Elección Municipal 2025', '2025-01-01 08:00:00', '2025-12-31 17:00:00');

INSERT INTO candidate (id, first_name, last_name, party, election_id) VALUES 
(1, 'Ana', 'García', 'Partido Liberal', 1),
(2, 'Carlos', 'López', 'Partido Conservador', 1),
(3, 'María', 'Rodríguez', 'Partido Verde', 1),
(4, 'José', 'Martínez', 'Partido Social', 1);