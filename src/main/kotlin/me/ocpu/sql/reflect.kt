@file:JvmName("ReflectionUtil")

package me.ocpu.sql

import java.lang.reflect.Field
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.kotlinProperty


//inline fun <reified R : Annotation> Collection<KProperty<*>>.filterHasAnnotation() =
//    filter { f -> f.annotations.firstOrNull { it is R } != null }
inline fun <reified R : Annotation> Collection<KProperty<*>>.filterNotHasAnnotation() =
    filter { f -> f.annotations.firstOrNull { it is R } == null }

inline fun <reified T> Class<*>.superClass(maxDepth: Int = 10): Class<T>? {
  var depth = 0
  var current = this
  val clazz = T::class.java
  do {
    if (depth == maxDepth)
      return null
    if (current == clazz)
      return current
    current = current.superclass
    depth++
  } while (true)
}

fun Field.setValue(instance: Any, value: Any) {
  val a = isAccessible
  if (!a)
    isAccessible = true
  if (name.endsWith("\$delegate")) {
    val delegate = get(instance)
    val method = delegate::class.java.declaredMethods.findLast { it.name == "setValue" }!!
    method.invoke(delegate, instance, kotlinProperty!!, value)
  }
  else set(instance, value)
  if (!a)
    isAccessible = false
}
fun Field.getValue(instance: Any) {
  val a = isAccessible
  if (!a)
    isAccessible = true
  if (name.endsWith("\$delegate")) {
    val delegate = get(instance)
    val method = delegate::class.java.declaredMethods.findLast { it.name == "getValue" }!!
    method.invoke(delegate, instance, kotlinProperty!!)
  }
  else get(instance)
  if (!a)
    isAccessible = false
}

//fun defaultConstructorMarker(): Any {
//  val clazz = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
//  val ctor = clazz.declaredConstructors.toList()[0]
//  ctor.isAccessible = true
//  val instance = ctor.newInstance()
//  ctor.isAccessible = false
//  return instance
//}
