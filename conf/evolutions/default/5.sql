# Commits Schema
 
# --- !Ups

CREATE TABLE branch (
    branchName varchar(128) NOT NULL PRIMARY KEY,
    lastKnownHead varchar(40) NOT NULL
);

# --- !Downs
 
DROP TABLE branch;
