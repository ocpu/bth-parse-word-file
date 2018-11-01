package me.ocpu.sql

import me.ocpu.converter.ClientsDB
import java.lang.reflect.AccessibleObject
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import java.sql.ResultSet
import kotlin.properties.Delegates.observable
import kotlin.properties.ObservableProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

@Suppress("UNCHECKED_CAST")
abstract class Model<E : Model<E>>(private val connection: Connection, private val table: String) {
  constructor(config: me.ocpu.sql.Database, table: String) : this(config.connection, table)

  private val identifiers by lazy {
    this::class.memberProperties.toMutableList()
        .filter { it !is KMutableProperty<*> || it.findAnnotation<Id>() != null }
        .filterIsInstance<KProperty<*>>()
  }
  private val fields by lazy {
    //    this::class.memberProperties.toMutableList()
//        .filterHasAnnotation<Field>()
//        .filterIsInstance<KMutableProperty<*>>()
    this::class.memberProperties.toMutableList()
        .filterNotHasAnnotation<Exclude>()
        .filterNotHasAnnotation<Id>()
        .filterIsInstance<KMutableProperty<*>>()
  }

  /**
   * Make a query to regather all values to the fields.
   */
  fun reset(useAuto: Boolean = true): E {
    if (identifiers.isEmpty())
      throw IndexOutOfBoundsException("there has to be at least 1 identifier")
    val ids = (if (useAuto) identifiers else identifiers.filterNotHasAnnotation<Auto>())
        .map { (it.findAnnotation<SerializedName>()?.value ?: it.name) to it }
    val stmt = connection
        .prepareStatement("SELECT * FROM $table WHERE ${ids.joinToString(" AND ") { "${it.first} = ?" }}")
    for ((i, id) in ids.withIndex()) {
      val value = id.second.call(this as E)
      stmt[i] = (value as? Enum<*>)?.toString() ?: value
    }
    val res = stmt.executeQuery()
    res.first()
    for (field in (fields + identifiers))
      setValue(field, res.getObject(getAsName(field)))
    return (this as E)
  }

  private fun setValue(field: KProperty<*>, value: Any) {
    val v = if (field.getter.call(this) is Enum<*>) try {
      (field.getter.call(this) as Enum<*>)::class.java.declaredMethods.last().invoke(null, value)
    } catch(_: InvocationTargetException) {
      ((field.getter.call(this) as Enum<*>)::class.java.declaredMethods.find { it.name == "values" }?.invoke(null) as Array<Enum<*>>)
          .find { it.toString() == value }!!
    }
    else value
    val accessible: Boolean
    val f = field.javaField
    if (f != null) {
      val obj = this as E
      accessible = f.isAccessible
      if (!accessible)
        AccessibleObject.setAccessible(arrayOf(f), true)
      val c = f.get(obj)
      when (c) {
        is ObservableProperty<*> -> {
          val s = c::class.java.superClass<ObservableProperty<*>>()
              ?: throw Error("Couldn't get observable property or value")
          s.getDeclaredField("value").setValue(c, v)
        }
        is DBValue<*, *> -> {
          val s = c::class.java.superClass<DBValue<*, *>>()
              ?: throw Error("Couldn't get DBValue property or value")
          s.getDeclaredField("value").setValue(c, v)
        }
        else -> f.set(obj, v)
      }
      if (!accessible)
        AccessibleObject.setAccessible(arrayOf(f), false)
    }
  }

  /**
   * Use this to make a update query to the database with all values in the model.
   */
  open fun update() {
    if (identifiers.isEmpty())
      throw IndexOutOfBoundsException("there has to be at least 1 identifier")
    this as E
    val stmt = connection
        .prepareStatement(
            "UPDATE " +
                table +
                " SET" +
                getAsNames(fields).joinToString(",") { " $it = ?" } +
                " WHERE" +
                getAsNames(identifiers).joinToString(" AND") { " $it = ?" }
        )
    for ((i, key) in fields.withIndex()) {
      try {
        val value = key.call(this)
        stmt[i] = (value as? Enum<*>)?.toString() ?: value
      } catch (e: InvocationTargetException) {
        e
      }
    }
    for ((i, key) in identifiers.withIndex()) {
      try {
        val value = key.call(this)
        stmt[fields.size + i] = (value as? Enum<*>)?.toString() ?: value
      } catch (e: InvocationTargetException) {
        e
      }
    }

    stmt.executeUpdate()
  }

  /**
   * Remove current object from the database.
   */
  open fun remove() {
    if (identifiers.isEmpty())
      throw IndexOutOfBoundsException("there has to be at least 1 identifier")
    val stmt = connection
        .prepareStatement("DELETE FROM $table WHERE ${identifiers.joinToString(" AND ") { "${it.name} = ?" }}")
    for ((i, f) in (fields + identifiers).withIndex()) {
      stmt.setObject(i + 1, f.getter.call((this as E)))
    }
    stmt.execute()
  }

  protected fun applyResultSet(resultSet: ResultSet) = Model.applyResultSet(this as E, resultSet)

  companion object {
    private fun getAsName(property: KProperty<*>) = property.findAnnotation<SerializedName>()?.value ?: property.name
    private fun getAsNames(list: List<KProperty<*>>) = list.map {
      it.findAnnotation<SerializedName>()?.value ?: it.name
    }

    /**
     * Fill a the [instance] with the [resultSet] fields.
     *
     * @param instance The model instance to fill in.
     * @param resultSet The result set to use.
     * @param M The [Model] child type.
     * @return [instance]
     */
    fun <M : Model<M>> applyResultSet(instance: M, resultSet: ResultSet): M {
      val meta = resultSet.metaData
      val columns = List(meta.columnCount) { meta.getColumnName(it + 1) }
//      instance.fields.filter { it.name in columns }.forEach {
//        instance.setValue(it, resultSet.getObject(it.name))
//      }
      instance::class.memberProperties
          .filterNotHasAnnotation<Exclude>()
          .asSequence()
          .map { (it.findAnnotation<SerializedName>()?.value ?: it.name) to it }
          .filter { it.first in columns }
          .forEach {
            val (name, prop) = it
            instance.setValue(prop, resultSet.getObject(name))
          }
      return instance
    }

    /**
     * If the primary constructor takes no arguments then you can use this to
     * create a model from a result set directly.
     */
    fun <M : Model<M>> fromResultSet(clazz: KClass<M>, resultSet: ResultSet): M {
      val ctor = clazz.primaryConstructor
      val instance = if (ctor != null) {
        val a = ctor.isAccessible
        if (!a) ctor.isAccessible = true
        ctor.isAccessible = true
        val instance = ctor.call()
        if (!a) ctor.isAccessible = false
        instance
      } else {
        clazz.java.newInstance() as M
      }

      val meta = resultSet.metaData
      val columns = List(meta.columnCount) { meta.getColumnName(it + 1) }
      (instance.fields + instance.identifiers)
          .filter { it.name in columns }
          .forEach {
            instance.setValue(it, resultSet.getObject(it.name))
          }
      return instance
    }

    /**
     * If the primary constructor takes no arguments then you can use this to
     * create a model from a result set directly.
     */
    @JvmStatic
    inline fun <reified M : Model<M>> fromResultSet(resultSet: ResultSet) = fromResultSet(M::class, resultSet)

    @JvmStatic
    fun <M : Model<M>> insert(model: M, vararg identifiers: Pair<KProperty<*>, Any>, block: (M.() -> Unit)? = null): M {
      for ((key, value) in identifiers)
        key.javaField?.setValue(model, value)
      if (block != null) model.block()
      insert(model)
      return model
    }

    @JvmStatic
    fun <M : Model<M>> insert(model: M): M {

      val keys = listOf(
          *model.identifiers.filterNotHasAnnotation<Auto>().toTypedArray(),
          *model.fields.toTypedArray()
      )
      val names = getAsNames(keys)
      val stmt = model.connection.prepareStatement(
          "INSERT INTO ${model.table} (${names.joinToString { "`$it`" }}) VALUES (${"?, ".repeat(keys.size).dropLast(2)})"
      )

      for ((i, key) in keys.withIndex()) {
        val value = key.call(model)
        stmt[i] = (value as? Enum<*>)?.toString() ?: value
      }

      stmt.executeUpdate()

      return model
    }
  }

  /**
   * Set a value of a [Id] field.
   */
  protected fun setIdentifier(name: String, value: Any) =
      identifiers.find { it.name == name }?.javaField?.setValue(this, value) ?: Unit

  override fun hashCode() = fields.fold(31) { acc, f -> acc * (f.getter.call((this as E))?.hashCode() ?: 1) }

  override fun toString(): String {
    val sb = StringBuilder(this::class.java.simpleName)
    sb.append('(')
    sb.append(fields.joinToString(",") {
      "${it.name}=${it.getter.call((this as E))}"
    })
    sb.append(')')
    return sb.toString()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Model<*>) return false

    if (table != other.table) return false
    if (identifiers != other.identifiers) return false
    if (fields != other.fields) return false

    return true
  }

  /**
   * Use a observable delegate to update the database automatically with the new value.
   * @param initialValue the initial value of the property.
   */
  fun <T> updating(initialValue: T) = observable(initialValue) { _, oldValue, newValue ->
    if (newValue == oldValue) return@observable
    (this as E).update()
  }

  fun <T> value(default: T) = DBValue<E, T>(default)
  class DBValue<M : Model<M>, T>(private var value: T) : ReadWriteProperty<M, T> {
    override fun getValue(thisRef: M, property: KProperty<*>) = value
    override fun setValue(thisRef: M, property: KProperty<*>, value: T) {
      if (this.value is Enum<*> && value is String) {
        val new = (this.value as Enum<*>)::class.java.getDeclaredMethod("valueOf").invoke(null, value)
        this.value = new as T
      } else this.value = value
    }
  }

  /**
   * A Model field like a name field for a user.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
  @MustBeDocumented
  annotation class Field

  /**
   * A model identifier like a books' isbn number.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
  @MustBeDocumented
  annotation class Id

  /**
   * A model identifier like a books' isbn number.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
  @MustBeDocumented
  annotation class Exclude

  /**
   * A model identifier like a books' isbn number.
   */
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
  @MustBeDocumented
  annotation class Auto

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
  @MustBeDocumented
  annotation class SerializedName(val value: String)
}
