# Commits Schema
 
# --- !Ups

CREATE TABLE artifact (
    id int NOT NULL PRIMARY KEY AUTO_INCREMENT,
    sha varchar(40) NOT NULL,
    filePath varchar(512) NOT NULL
);

# --- !Downs
 
DROP TABLE artifact;
