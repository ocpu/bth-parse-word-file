package me.ocpu.converter

import org.docx4j.Docx4J
import org.docx4j.wml.*
import java.io.File
import java.io.InputStream
import javax.xml.bind.JAXBElement


fun wordFileToSections(file: File) = wordFileToSections(file.inputStream())
fun wordFileToSections(stream: InputStream): List<Section> {
  val sections = mutableListOf<Section>()
  val content = Docx4J.load(stream).mainDocumentPart.content
  val iterator = content.listIterator()
  while (iterator.hasNext()) {
    val section = iterator.next().cast<P>().string
    val table = try {
      iterator.next().cast<Tbl>()
    } catch (_: ClassCastException) {
      break
    }
    val rows = table.content
    val pairs = Array(rows.size) {
      val row = try {
        rows[it].cast<Tr>()
      } catch (_: java.lang.ClassCastException) {
        return@Array "" to ""
      }
      val key = try {
        row.child<Tc>(0).string
      }
      catch (_: IndexOutOfBoundsException) { "" }
      catch (_: java.lang.ClassCastException) { "" }
      val value = try {
        row.child<Tc>(1).string
      }
      catch (_: IndexOutOfBoundsException) { "" }
      catch (_: java.lang.ClassCastException) { "" }
      key to value
    }
    sections.add(Section(section, pairs))
    try {
      iterator.next().cast<P>()
      iterator.next().cast<P>()
      iterator.previous()
    } catch (_: ClassCastException) {
      iterator.previous()
      iterator.previous()
    } catch (_: IndexOutOfBoundsException) {
      break
    }
  }
  return sections
}

private inline fun <reified T> Any.cast() = when (this) {
  is T -> this
  is JAXBElement<*> ->
    if (this.value is T) this.value as T
    else throw ClassCastException("${this.value::class.java.name} cannot be cast to ${T::class.java.name}")
  else -> throw ClassCastException("${this::class.java.name} cannot be cast to ${T::class.java.name}")
}

private inline fun <reified T> ContentAccessor.child(n: Number) = content[n.toInt()].cast<T>()

private val Tc.string: String
  get() = buildString {
    val iterator = content.listIterator()
    while (iterator.hasNext()) {
      try {
        val item = iterator.next()
        val text = item.cast<P>().string
        append(text)
        if (text[text.length - 1] != ' ')
          append("\n")
      } catch (_: IndexOutOfBoundsException) {
      }
    }
  }.trim()
private val P.string: String
  get() = buildString {
    val iterator = content.listIterator()
    while (iterator.hasNext()) {
      try {
        val item = iterator.next()
        val r = item.cast<R>()
        val text = getText(r)
        append(text)
//        if (text[text.length - 1] != ' ')
//          append(" ")
      } catch (_: ClassCastException) {
      } catch (_: IndexOutOfBoundsException) {
      }
    }
  }.trim()

private fun getText(item: R): String {
  val iterator = item.content.listIterator()
  val sb = StringBuilder()
  loop@ while (iterator.hasNext()) {
    val i = iterator.next()
    try {
      val text = i.cast<Text>().value
      sb.append(if (!text.startsWith(" FORM")) text.trim() else "")
    } catch (_: ClassCastException) {
      val fc = i.cast<FldChar>()
      if (fc.ffData == null) continue@loop
      val data = fc.ffData.nameOrEnabledOrCalcOnExit
      val control = when {
        data.size > 3 -> data[3].value
        data[2].value !is BooleanDefaultTrue -> data[2].value
        else -> continue@loop
      }
      return when (control) {
        is CTFFCheckBox -> if ((control.checked ?: return " ").isVal) "X" else " "
        is CTFFDDList ->
          if (control.result == null) " "
          else control.listEntry[control.result.`val`.toInt()].`val`
        is CTFFTextInput -> control.default?.`val`?.trim() ?: ""
        else -> "DATA"
      }
    }
    if (iterator.hasNext())
      sb.append("\n")
  }
  return sb.toString()
}

class Section(val name: String, val pairs: Array<Pair<String, String>>) {
  override fun toString() = name
}
