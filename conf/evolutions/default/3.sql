# Commits Schema
 
# --- !Ups

CREATE TABLE login (
    id serial NOT NULL PRIMARY KEY,
    email varchar(128) NOT NULL
);

CREATE TABLE setting (
    name varchar(128) NOT NULL PRIMARY KEY,
    value varchar(512) NOT NULL
);

# --- !Downs
 
DROP TABLE login;
DROP TABLE setting;
