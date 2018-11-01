package me.ocpu.sql

import com.mysql.cj.jdbc.ConnectionImpl
import org.intellij.lang.annotations.Language
import java.sql.*
import java.util.*

class Database(private var _connection: Connection) {
  constructor(user: String,
              password: String,
              protocol: String = "jdbc",
              provider: String = "mysql",
              host: String = "localhost",
              port: Int = 3306,
              database: String = "") :
      this(createConnection(user, password, protocol, provider, host, port, database))

  val connection get() = _connection

  val schemas: List<String>
    get() = executeToList("SHOW DATABASES") {
      it.getString(1)
    }

  @Suppress("UsePropertyAccessSyntax")
  var schema = connection.schema
      set(value) {
        val (urlPartA, urlPartB) = (_connection as ConnectionImpl).url.split("/[\\w\\d_\\-]*\\?".toRegex())
        val passField = ConnectionImpl::class.java.getDeclaredField("password")
        passField.isAccessible = true
        val password = passField.get(_connection) as String
        passField.isAccessible = false
        _connection = DriverManager.getConnection(
            "$urlPartA/$value?$urlPartB",
            (_connection as ConnectionImpl).user,
            password
        )
        field = value
      }

  fun execute(@Language("sql") sql: String, vararg params: Any): Optional<ResultSet> {
    val stmt = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
    for ((i, param) in params.withIndex())
      stmt[i] = param
    return if (stmt.execute()) {
      val res = stmt.resultSet
      if (!res.first()) Optional.empty()
      else Optional.ofNullable(res)
    } else Optional.empty()
  }

  inline fun <T> execute(@Language("sql") sql: String, vararg params: Any, crossinline res: (ResultSet) -> T) =
    res(execute(sql, *params).get())

  inline fun <T> executeIfPresent(@Language("sql") sql: String, vararg params: Any, crossinline res: (ResultSet) -> T) {
    val result = execute(sql, *params)
    if (result.isPresent)
      res(result.get())
  }

  inline fun <T> executeToList(@Language("sql") sql: String, vararg params: Any, crossinline res: (ResultSet) -> T): List<T> {
    val result = execute(sql, *params).get()
    if (!result.last()) return emptyList()
    val size = result.row
    result.beforeFirst()
    return List(size) {
      result.next()
      res(result)
    }.also { result.close() }
  }

  fun newBlob(): Blob = connection.createBlob()
  fun newClob(): Clob = connection.createClob()
  fun newBlob(bytes: ByteArray): Blob = connection.createBlob().also { it.setBytes(0, bytes) }
  fun newClob(str: String): Clob = connection.createClob().also { it.setString(0, str) }

  inline fun <T> transaction(crossinline block: () -> T): T {
    val auto = try { connection.autoCommit } catch (_: NullPointerException) { true }
    return try {
      if (auto)
        connection.autoCommit = false
      connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
      val ret = block()
      connection.commit()
      ret
    } catch (e: SQLException) {
      connection.rollback()
      throw e
    } finally {
      try {
        if (auto)
          connection.autoCommit = true
      } catch (_: NullPointerException) {}
      catch (e: Throwable) {
        throw e
      }
    }
  }

  companion object {
    fun createConnection(
        user: String,
        password: String,
        protocol: String = "jdbc",
        provider: String = "mysql",
        host: String = "localhost",
        port: Int = 3306,
        database: String = "",
        useSSL: Boolean = true
    ): java.sql.Connection =
        DriverManager.getConnection("$protocol:$provider://$host${if (port != -1) ":$port" else ""}/$database?useTimezone=true&serverTimezone=UTC&useSSL=$useSSL", user, password)
  }
}