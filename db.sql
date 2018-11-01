DROP DATABASE IF EXISTS clients;
CREATE DATABASE clients;

DROP USER IF EXISTS 'ocpu'@'localhost';
CREATE USER 'ocpu'@'localhost'
  IDENTIFIED BY 'test';
GRANT ALL PRIVILEGES ON clients.* TO 'ocpu'@'localhost'
WITH GRANT OPTION;

USE clients;

DROP TABLE IF EXISTS database_server_databases;
DROP TABLE IF EXISTS licenses;
DROP TABLE IF EXISTS arcgis;
DROP TABLE IF EXISTS database_servers;
DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS environments;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
  object_id    INT         NOT NULL AUTO_INCREMENT,
  name         VARCHAR(64) NOT NULL,
  id           INT         NOT NULL,
  shape_area   DOUBLE      NOT NULL,
  shape_length DOUBLE      NOT NULL,

  PRIMARY KEY (id),
  INDEX (object_id, id, name)
)
  COMMENT 'This is the table that holds the customers of the organization.';

CREATE TABLE environments (
  id                INT AUTO_INCREMENT                    NOT NULL,
  customer_id       INT                                   NOT NULL,
  technician        TEXT,
  date              DATE,
  system_number     VARCHAR(10),
  version           TEXT,
  coordinate_system TEXT,
  type              ENUM ('PRODUCTION', 'TEST', 'PORTAL') NOT NULL,
  install_files     TEXT,

  PRIMARY KEY (id),
  INDEX (type, customer_id),
  FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE TABLE licenses (
  environment_id INT NOT NULL,
  name           VARCHAR(255),
  version        VARCHAR(32),

  FOREIGN KEY (environment_id) REFERENCES environments (id)
);

CREATE TABLE arcgis (
  environment_id       INT,
  name                 VARCHAR(32),
  ip                   VARCHAR(15),
  operating_system     ENUM ('Windows 2012 R2', 'Windows 2012', 'Windows 2008 R2', 'Windows 2008'),
  architecture         ENUM ('x86', 'x64'),
  service_pack         ENUM ('NONE', 'SP1', 'SP2', 'SP3', 'SP4'),
  `virtual`            BOOLEAN,
  manager              TEXT,
  server_folder        TEXT,
  user_folder          TEXT,
  document_folder      TEXT,
  install_folder       TEXT,
  address              TEXT,
  database_compression TEXT
)
  COMMENT '';

CREATE TABLE database_servers (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  environment_id   INT,
  name             VARCHAR(32),
  ip               VARCHAR(15),
  operating_system ENUM ('Windows 2012 R2', 'Windows 2012', 'Windows 2008 R2', 'Windows 2008'),
  architecture     ENUM ('x86', 'x64'),
  service_pack     ENUM ('NONE', 'SP1', 'SP2', 'SP3', 'SP4'),
  `virtual`        BOOLEAN,
  db_version       TEXT,
  db_architecture  ENUM ('x86', 'x64'),
  db_service_pack  ENUM ('NONE', 'SP1', 'SP2', 'SP3', 'SP4')
)
  COMMENT '';

CREATE TABLE database_server_databases (
  id                 VARCHAR(32),
  description        TEXT,
  database_server_id INT,

  INDEX (id),
  FOREIGN KEY (database_server_id) REFERENCES database_servers (id)
);

CREATE TABLE services (
  environment_id INT  NOT NULL,
  name           VARCHAR(255),
  server         VARCHAR(64),
  ip             VARCHAR(15),
  install_folder TEXT,
  program_pool   TINYTEXT,
  address        TEXT,
  extra          TEXT NOT NULL,

  INDEX (environment_id, name),
  FOREIGN KEY (environment_id) REFERENCES environments (id)
)
  COMMENT 'This holds all unspecified services the customer has.';
