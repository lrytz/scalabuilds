# Commits Schema
 
# --- !Ups

CREATE TABLE branch (
    name varchar(128) NOT NULL PRIMARY KEY,
    lastKnownHead varchar(40) NOT NULL
);

# --- !Downs
 
DROP TABLE branch;
