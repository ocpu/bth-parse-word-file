package me.ocpu.converter

import me.ocpu.sql.Database
import me.ocpu.sql.setValue
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage
import org.xlsx4j.sml.STCellType
import java.io.File
import java.util.*

// Allt som inte är drift och om det inte har en test är en drift !!!!!!

fun main(args: Array<String>) {
  reset()
  if (args.isEmpty()) System.err.println("Please specify files to insert into the database")

  val filesPool = LinkedList<File>()

  for (arg in args) {
    val file = File(arg)
    if (file.isDirectory)
      file.list().filter { name -> name.endsWith(".docx") }.map { File(file, it) }.forEach(filesPool::addLast)
    else filesPool.add(file)
  }

  for (i in 0 until 10) /*thread*/ {
    while (filesPool.isNotEmpty()) convertAndInsert(
        synchronized(filesPool) { filesPool.removeFirst() },
        filesPool
    )
  }
}

fun reset() = with(Database(user = "ocpu", password = "test", database = "clients")) {

  try {
    execute("DELETE FROM database_server_databases;")
    execute("DELETE FROM licenses;")
    execute("DELETE FROM arcgis;")
    execute("DELETE FROM database_servers;")
    execute("DELETE FROM services;")
    execute("DELETE FROM environments;")
    execute("DELETE FROM customers;")
    insertCustomers(this)
  } catch (e: Throwable) {
    e.printStackTrace()
  }
}

val serverIpKey = "Server\\s?\\(IP\\)".toRegex()
val serverIpValue = "([\\w\\d.]+)\\s?\\((?:[\\w\\d]+,\\s)?(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\)".toRegex()
val externArcGISSection = "Extern\\s?ArcGIS\\s?Server".toRegex()
val filteredKeys = arrayOf("Installationskatalog", "Programpool", "Adress", "Server (IP)")
fun convertAndInsert(file: File, files: List<File>) {
  val sections = LinkedList(wordFileToSections(file))
  ClientsDB.db.transaction {

    val ci = getCustomerInfo(sections, file) // Get customer info and environment info
    val customer = ClientsDB.Customer.get(ci.kund) ?: ClientsDB.Customer.new(ci) // Get / make customer
    val environment = ClientsDB.Environment.new(
        ci,
        customer,
        ClientsDB.Environment.Type.getFromFileName(file, files) // Get environment type
    ) // Make environment

    // Get special sections
    handleLicense(sections, environment)
    handleDatabaseServer(sections, environment)
    handleArcGIS(sections, environment)
    handleInstallFiles(sections, environment)

    // Iterate over the rest of the sections aka services
    for (section in sections) {
      val iterator = section.pairs.toList().listIterator()
      while (iterator.hasNext()) {
        val (key, value) = iterator.next()
        if (serverIpKey.matches(key)) {
          val (_, server, ip) = if (value.isBlank()) arrayListOf<String?>(null, null, null) else try {
            serverIpValue.find(value)?.groupValues ?: arrayListOf<String?>(null, null, null)
          } catch (_: IndexOutOfBoundsException) {
            arrayListOf<String?>(null, null, null)
          }
          val map = mutableMapOf<String, String>()
          while (true) {
            if (!iterator.hasNext()) break
            val (k, v) = iterator.next()
            if (k == "Server (IP)") {
              iterator.previous()
              break
            }
            map[k] = v
          }
          val name = if (section.name.startsWith("GEOSECMA") && section.name[8] != ' ')
            section.name.replace("GEOSECMA", "GEOSECMA ")
          else section.name
          ClientsDB.Service.new(
              environment, name, server, ip,
              map.remove("Installationskatalog"), map.remove("Programpool"), map.remove("Adress"),
              '{' +
                  map.entries
                      .asSequence()
                      .filter { it.key !in filteredKeys }
                      .joinToString(",") { (key, value) -> "\"$key\":\"$value\"" } +
                  '}'
          )
        }
      }
    }
  }
}

fun insertCustomers(database: Database) {
  val workbook = SpreadsheetMLPackage.load(File("KommunerToDelivery.xlsx")).workbookPart
  val ws0 = workbook.getWorksheet(0)
  val strings = workbook.sharedStrings.contents.si.map { it.t.value }
  val data = ws0.jaxbElement.sheetData.row.asSequence().map { row ->
    row.c.map { cell ->
      val value = when (cell.t) {
        STCellType.S -> strings[cell.v.toInt()]
        else -> cell.v ?: ""
      }
      try {
        value.toInt()
      } catch (e: NumberFormatException) {
        value
      }
    }.dropLast(1)
  }.toMutableList()
  data.removeAt(0)
  database.execute("INSERT INTO customers (object_id, `name`, id, shape_area, shape_length) VALUES ${
  data.joinToString(",") { row -> '(' + row.joinToString(",") { "?" } + ')' }
  }", *data.flatten().toTypedArray())
}

private fun getCustomerInfo(sections: LinkedList<Section>, file: File): CustomerInfo {
  val info = sections.find { it.name == "Kunduppgifter" }!!
  sections.remove(info)
  val ci = CustomerInfo()
  for ((key, value) in info.pairs) {
    try {
      val field = CustomerInfo::class.java.getDeclaredField(key.toLowerCase())
      field.setValue(ci, value)
    } catch (_: Throwable) {
    }
  }
  ci.kund = file.nameWithoutExtension.split('_', limit = 2)[0]
  return ci
}

private fun handleLicense(sections: LinkedList<Section>, environment: ClientsDB.Environment) {
  val licenseServer = sections.find { it.name == "Licensserver" }
  if (licenseServer != null && licenseServer.pairs.isNotEmpty()) {
    sections.remove(licenseServer)
    val res = Regex("([\\w\\d]+)\\s\\(((?:[\\w\\d]+,\\s)?[\\d.]+)\\)").find(licenseServer.pairs[0].second)
    if (res != null && res.groupValues.isNotEmpty())
      ClientsDB.License.new(environment, res.groupValues[1], res.groupValues[2])
  }
}

private fun handleArcGIS(sections: LinkedList<Section>, environment: ClientsDB.Environment) {
  val arcGISServer = sections.find { "ArcGIS\\s?Server".toRegex().matches(it.name) }

  if (arcGISServer != null && arcGISServer.pairs.isNotEmpty()) {
    sections.remove(arcGISServer)
    val (_, server, ip) = "([\\w\\d]+)\\s?\\((?:[\\w\\d]+,\\s)?(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\)"
        .toRegex().find(arcGISServer.pairs.find { it.first == "Server (IP)" }?.second ?: "")?.groupValues
        ?: listOf(null, null, null)
    val operatingSystem = arcGISServer.pairs.find { it.first == "Operativsystem" }?.second
    if (operatingSystem != null) {
      val os = ClientsDB.OS.values().find { it.toString() in operatingSystem }
          ?: ClientsDB.OS.Windows_2012_R2
      val sp = ClientsDB.ServicePack.values().find { it.toString() in operatingSystem }
          ?: ClientsDB.ServicePack.NONE
      val virtual = "XVirtuell" in operatingSystem
      val arch = if ("Xx64" in operatingSystem)
        ClientsDB.Architecture.x64
      else ClientsDB.Architecture.x86
      val manager = arcGISServer.pairs.find { it.first == "Server Manager" }?.second ?: ""
      val serverFolder = arcGISServer.pairs.find { it.first == "Serverkatalog" }?.second ?: ""
      val userFolder = arcGISServer.pairs.find { it.first == "Användarkatalog" }?.second ?: ""
      val documentFolder = arcGISServer.pairs.find { it.first == "Dokumentkatalog - Delning" }?.second ?: ""
      val installFolder = arcGISServer.pairs.find { it.first == "Installationskatalog WA" }?.second ?: ""
      val address = arcGISServer.pairs.find { it.first == "Adress WA" }?.second ?: ""
      val databaseCompression = arcGISServer.pairs.find { it.first == "Databaskomprimering" }?.second ?: ""

      ClientsDB.ArcGIS.new(environment, server ?: "", ip ?: "", os, arch, sp, virtual,
          manager, serverFolder, userFolder, documentFolder, installFolder, address, databaseCompression)
    }
  }
}

private fun handleInstallFiles(sections: LinkedList<Section>, environment: ClientsDB.Environment) {
  val installationFiles = sections.find { it.name == "Installationsfiler" }

  if (installationFiles != null && installationFiles.pairs.isNotEmpty()) {
    sections.remove(installationFiles)
    val path = installationFiles.pairs[0].first.split(' ', limit = 2)[0]
    environment.type
    environment.installFiles = path
    environment.update()
  }
}

private fun handleDatabaseServer(sections: LinkedList<Section>, environment: ClientsDB.Environment) {
  val databaseServer = sections.find { it.name == "Databasserver" }

  if (databaseServer != null && databaseServer.pairs.isNotEmpty()) {
    sections.remove(databaseServer)
    val (_, server, ip) = serverIpValue.find(databaseServer.pairs.find { serverIpKey.matches(it.first) }?.second ?: "")?.groupValues
        ?: listOf(null, null, null)
    val operatingSystem = databaseServer.pairs.find { it.first == "Operativsystem" }?.second!!
    val os = ClientsDB.OS.values().find { it.toString() in operatingSystem }
        ?: ClientsDB.OS.Windows_2012_R2
    val sp = ClientsDB.ServicePack.values().find { it.toString() in operatingSystem }
        ?: ClientsDB.ServicePack.NONE
    val virtual = "XVirtuell" in operatingSystem
    val arch = if ("Xx64" in operatingSystem)
      ClientsDB.Architecture.x64
    else ClientsDB.Architecture.x86
    val version = databaseServer.pairs.find { it.first == "Version" }?.second!!
    val databaseVersion = version.split(' ', limit = 2)[0].trim()
    val databaseServicePack = ClientsDB.ServicePack.values().find { it.toString() in version }
        ?: ClientsDB.ServicePack.NONE
    val databaseArchitecture = if ("Xx64" in version)
      ClientsDB.Architecture.x64
    else ClientsDB.Architecture.x86
    val ds = ClientsDB.DatabaseServer.new(
        environment, server!!, ip!!,
        os, arch, sp, virtual,
        databaseVersion, databaseArchitecture, databaseServicePack
    )
    val databases = databaseServer.pairs[1 + databaseServer.pairs.indexOfFirst { it.first == "Databaser" }]
    val ids = databases.first.split('\n')
    val descriptions = databases.second.split('\n')
    for ((i, id) in ids.withIndex())
      ClientsDB.DatabaseServerDatabase.new(
          ds,
          id.replace("(enter för ny rad)", ""),
          descriptions[i].replace("(enter för ny rad)", "")
      )
  }
}

class CustomerInfo {
  lateinit var kund: String
  //  lateinit var kontaktperson: String
  lateinit var tekniker: String
  lateinit var datum: String
  var systemnummer: String = ""
  lateinit var version: String
  var koordinatsystem: String = ""
}
