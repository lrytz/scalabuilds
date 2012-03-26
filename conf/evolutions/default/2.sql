# Commits Schema
 
# --- !Ups

CREATE TABLE artifact (
    id serial NOT NULL PRIMARY KEY,
    sha varchar(40) NOT NULL,
    filePath varchar(512) NOT NULL
);

# --- !Downs
 
DROP TABLE artifact;
