package me.ocpu.converter

import me.ocpu.sql.Database
import me.ocpu.sql.Model
import java.io.File
import java.sql.Date

object ClientsDB {

  val db = Database(user = "ocpu", password = "test")
  init {
    if ("clients" !in db.schemas)
      createDatabase()
    db.schema = "clients"
  }

  class Customer private constructor() : Model<Customer>(db, "customers") {
    @Id
    @Auto
    val id by value(0)
    var name by value("")

    companion object {
      fun new(ci: CustomerInfo) = Model.insert(Customer()) { name = ci.kund }.reset(false)
      fun get(customer: String): Customer? {
        val res = db.execute("SELECT * FROM customers WHERE name = ?", customer)
        return if (!res.isPresent) null
        else Model.fromResultSet<Customer>(res.get())
      }
    }
  }

  class Service private constructor() : Model<Service>(db, "services") {
    @Id
    @SerializedName("environment_id")
    val environmentId by value(0)
    var name by value("")
    var server by value<String?>(null)
    var ip by value<String?>(null)
    @SerializedName("install_folder")
    var installFolder by value<String?>(null)
    @SerializedName("program_pool")
    var programPool by value<String?>(null)
    var address by value<String?>(null)
    var extra by value("")

    companion object {
      fun new(environment: Environment, name: String, server: String?, ip: String?,
              installFolder: String?, programPool: String?, address: String?,
              extra: String = "{}"): Service {
        val service = Service()
        Model.insert(service, Service::environmentId to environment.id) {
          this.server = server
          this.name = name
          this.ip = ip
          this.installFolder = installFolder
          this.programPool = programPool
          this.address = address
          this.extra = extra
        }
        return service
      }
    }
  }

  class Environment private constructor() : Model<Environment>(db, "environments") {
    @Id
    @Auto
    val id by value(0)
    @Id
    @SerializedName("customer_id")
    val customerId by value(0)
    var technician by value("")
    var date by value(Date(0))
    var version by value("")
    @SerializedName("coordinate_system")
    var coordinateSystem by value("")
    @SerializedName("system_number")
    var systemNumber by value("")
    @Id
    val type = Type.NONE
    @SerializedName("install_files")
    var installFiles by value("")

    enum class Type {
      PRODUCTION, TEST, PORTAL, NONE;

      companion object {
        fun getFromFileName(file: File, files: List<File>): Type {
          val name = file.nameWithoutExtension
          return when {
            name.contains("drift", true) -> Type.PRODUCTION
            name.contains("test", true) -> Type.TEST
            name.contains("portal", true) -> Type.PORTAL
            else -> {
              val customer = name.split("_", limit = 2)[0]
              val test = files.find { customer in it.name && it.name.contains("test", true) }
              if (test == null) Type.PRODUCTION
              else Type.NONE
            }
          }
        }
      }
    }

    companion object {
      fun new(ci: CustomerInfo, customer: Customer, type: Type): Environment {
        val env = Model.insert(
            Environment(),
            Environment::customerId to customer.id,
            Environment::type to type) {
          technician = ci.tekniker
          date = Date.valueOf(ci.datum)
          version = ci.version
          coordinateSystem = ci.koordinatsystem
          systemNumber = ci.systemnummer
        }
        db.connection.commit()
//        db.transaction {
//          db.execute("INSERT INTO environments (customer_id, technician, date, system_number, version, coordinate_system, type) VALUES (?, ?, ?, ?, ?, ?, ?)",
//              env.customerId, env.technician, env.date, env.systemNumber, env.version, env.coordinateSystem, env.type.toString())
//        }
//        val res = db.execute("SELECT * FROM environments WHERE customer_id = ? AND type = ?", env.customerId, env.type)
        env.reset(false)
        return env
      }
    }
  }

  class License private constructor() : Model<License>(db, "licenses") {
    @SerializedName("environment_id")
    val environmentId by value(0)
    var name by value("")
    var version by value("")

    companion object {
      fun new(environment: Environment, name: String, version: String) =
          Model.insert(License(), License::environmentId to environment.id) {
            this.name = name
            this.version = version
          }
    }
  }

  class ArcGIS private constructor() : Model<ArcGIS>(db, "arcgis") {
    @SerializedName("environment_id")
    val environmentId by value(0)
    var name by value("")
    var ip by value("")
    @SerializedName("operating_system")
    var operatingSystem by value(OS.Windows_2008)
    var architecture by value(Architecture.x86)
    @SerializedName("service_pack")
    var servicePack by value(ServicePack.NONE)
    var virtual by value(false)
    var manager by value("")
    @SerializedName("server_folder")
    var serverFolder by value("")
    @SerializedName("user_folder")
    var userFolder by value("")
    @SerializedName("document_folder")
    var documentFolder by value("")
    @SerializedName("install_folder")
    var installFolder by value("")
    var address by value("")
    @SerializedName("database_compression")
    var databaseCompression by value("")

    companion object {
      fun new(environment: Environment, name: String, ip: String, operatingSystem: OS, architecture: Architecture,
              servicePack: ServicePack, virtual: Boolean, manager: String, serverFolder: String, userFolder: String,
              documentFolder: String, installFolder: String, address: String, databaseCompression: String) =
          Model.insert(ArcGIS(), ArcGIS::environmentId to environment.id) {
            this.name = name
            this.ip = ip
            this.operatingSystem = operatingSystem
            this.architecture = architecture
            this.servicePack = servicePack
            this.virtual = virtual
            this.manager = manager
            this.serverFolder = serverFolder
            this.userFolder = userFolder
            this.documentFolder = documentFolder
            this.installFolder = installFolder
            this.address = address
            this.databaseCompression = databaseCompression
          }
    }
  }

  class DatabaseServer private constructor() : Model<DatabaseServer>(db, "database_servers") {
    @Auto
    val id by value(0)
    @SerializedName("environment_id")
    val environmentId by value(0)
    var name by value("")
    var ip by value("")
    @SerializedName("operating_system")
    var operatingSystem by value(OS.Windows_2008)
    var architecture by value(Architecture.x86)
    @SerializedName("service_pack")
    var servicePack by value(ServicePack.NONE)
    var virtual by value(false)
    @SerializedName("db_version")
    var databaseVersion by value("")
    @SerializedName("db_architecture")
    var databaseArchitecture by value(Architecture.x86)
    @SerializedName("db_service_pack")
    var databaseServicePack by value(ServicePack.NONE)

    companion object {
      fun new(environment: Environment, name: String, ip: String,
              operatingSystem: OS, architecture: Architecture, servicePack: ServicePack, virtual: Boolean,
              databaseVersion: String, databaseArchitecture: Architecture, databaseServicePack: ServicePack): DatabaseServer {
        val ds = Model.insert(DatabaseServer(), DatabaseServer::environmentId to environment.id) {
          this.name = name
          this.ip = ip
          this.operatingSystem = operatingSystem
          this.architecture = architecture
          this.servicePack = servicePack
          this.virtual = virtual
          this.databaseVersion = databaseVersion
          this.databaseArchitecture = databaseArchitecture
          this.databaseServicePack = databaseServicePack
        }

        db.connection.commit()

        ds.reset(false)

        return ds
      }
    }
  }

  class DatabaseServerDatabase private constructor() : Model<DatabaseServerDatabase>(db, "database_server_databases") {
    val database_server_id by value(0)
    var id by value("")
    var description by value("")

    companion object {
      fun new(databaseServer: DatabaseServer, id: String, description: String) =
          Model.insert(DatabaseServerDatabase(), DatabaseServerDatabase::database_server_id to databaseServer.id) {
            this.id = id
            this.description = description
          }
    }
  }

  enum class OS {
    Windows_2012_R2, Windows_2012, Windows_2008_R2, Windows_2008;
    override fun toString() = name.replace('_', ' ')
  }
  enum class Architecture { x86, x64 }
  enum class ServicePack { NONE, SP1, SP2, SP3, SP4 }

  fun createDatabase() {
    db.transaction {
      with(db) {
        execute("DROP DATABASE IF EXISTS clients")
        execute("CREATE DATABASE clients")
        //language=MySQL
        execute("""
DROP TABLE IF EXISTS clients.environments;
DROP TABLE IF EXISTS clients.services;
DROP TABLE IF EXISTS clients.customers;
          """.trimIndent())
        //language=MySQL
        execute("""
CREATE TABLE clients.customers (
  id   INT AUTO_INCREMENT NOT NULL,
  name TINYTEXT,

  PRIMARY KEY (id),
  INDEX (id)
) COMMENT 'This is the table that holds the customers of the organization.';
          """.trimIndent())
        //language=MySQL
        execute("""
CREATE TABLE environments (
  id                INT AUTO_INCREMENT                    NOT NULL,
  customer_id       INT                                   NOT NULL,
  technician        TEXT,
  date              DATE,
  system_number     VARCHAR(10),
  version           TEXT,
  coordinate_system TEXT,
  type              ENUM ('PRODUCTION', 'TEST', 'PORTAL') NOT NULL,

  PRIMARY KEY (id),
  INDEX (type, customer_id),
  FOREIGN KEY (customer_id) REFERENCES customers (id)
);
          """.trimIndent())
        //language=MySQL
        execute("""
CREATE TABLE services (
  environment_id INT  NOT NULL,
  name           VARCHAR(255),
  server         VARCHAR(10),
  ip             VARCHAR(15),
  install_folder TEXT,
  program_pool   TINYTEXT,
  address        TEXT,
  extra          TEXT NOT NULL,

  INDEX (environment_id, name),
  FOREIGN KEY (environment_id) REFERENCES environments (id)
) COMMENT 'This holds all unspecified services the customer has.';
          """.trimIndent())
      }
    }
  }
}
