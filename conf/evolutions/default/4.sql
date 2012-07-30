# Commits Schema
 
# --- !Ups

CREATE TABLE buildUUID (
    uuid varchar(36) NOT NULL PRIMARY KEY,
    jenkinsBuild int NOT NULL
);

# --- !Downs
 
DROP TABLE buildUUID;
