# Commits Schema
 
# --- !Ups

CREATE TABLE commit (
    sha varchar(40) NOT NULL PRIMARY KEY,
    commitDate bigint NOT NULL,
    githubUser varchar(255),
    authorName varchar(255) NOT NULL,
    state varchar(31) NOT NULL,
    jenkinsBuild int,
    jenkinsBuildUUID varchar(36),
    buildSuccess boolean
);

# --- !Downs
 
DROP TABLE commit;
