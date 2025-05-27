create user electuser with password '123456';
create database votaciones owner electuser;
grant connect on database votaciones to electuser;

create database elections owner electuser;
grant connect on database elections to electuser;
