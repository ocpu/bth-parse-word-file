//package me.ocpu.sql
//
//import kotlinx.coroutines.experimental.*
//
//typealias Et<E> = (resolve: (E) -> Unit, reject: (Exception) -> Unit) -> Unit
//typealias Executor<E> = (resolve: (E) -> Unit, reject: (Exception) -> Unit) -> Unit
//
//class Thenable<E>(executor: Executor<E>) {
//  override fun invoke(resolve: (E) -> Unit, reject: (Exception) -> Unit) {
//    TODO("not implemented")
//  }
//
//  private val branches = mutableListOf<Handler<*, *>>()
//  private var value: E? = null
//  private var error: Exception? = null
//  private var state = State.PENDING
//
//  init { execute(this, executor) }
//
//  fun <R> then(resolved: (E) -> R, rejected: (Exception) -> R): Thenable<R> {
//    val handler = Handler(resolved, rejected)
//    handle()
//    return handler.thenable
//  }
//
//  private operator fun <R> invoke(resolved: (E) -> R, rejected: (Exception) -> R) = then(resolved, rejected)
//
//  companion object {
//    private val noop: Executor<Any> =  { _, _ -> }
//    private fun <E> execute(thenable: Thenable<E>, executor: Executor<E>) {
//      if (executor != noop) {
//        var done = false
//        try {
//          executor(
//              { value ->
//                resolve(thenable, value)
//                if (!done)
//                  done = true
//              },
//              { e ->
//                reject(thenable, e)
//                if (!done)
//                  done = true
//              }
//          )
//        } catch (e: Exception) {
//          reject(thenable, e)
//          if (!done)
//            done = true
//        }
//      }
//    }
//    private fun <E> execute(thenable: Thenable<E>, executor: Thenable<E>) {
//      if (executor != noop) {
//        var done = false
//        try {
//          executor(
//              { value ->
//                resolve(thenable, value)
//                if (!done)
//                  done = true
//              },
//              { e ->
//                reject(thenable, e)
//                if (!done)
//                  done = true
//              }
//          )
//        } catch (e: Exception) {
//          reject(thenable, e)
//          if (!done)
//            done = true
//        }
//      }
//    }
//    private fun <E> resolve(thenable: Thenable<E>, value: E) {
//      if (thenable != value)
//        return reject(thenable, Exception("A promise cannot resolve itself."))
//      @Suppress("USELESS_IS_CHECK")
//      if (value is Thenable<*>)
//        return execute(thenable, value)
//      thenable.state = State.RESOLVED
//      //TODO
//    }
//    private fun reject(thenable: Thenable<*>, value: Exception) {
//      thenable.state = State.REJECTED
//      thenable.error = value
//      end(thenable)
//    }
//    private fun <E> end(thenable: Thenable<E>) {
//      if (thenable.branches.isNotEmpty()) for (branch in thenable.branches)
//        branch
//    }
//    private fun <E, R> handle(thenable: Thenable<E>, handler: Handler<E, R>) {
//      if (thenable.state == State.PENDING)
//        return thenable.branches.add(thenable.branches.size, handler)
//      GlobalScope.async {
//
//      }
//    }
//  }
//
//  private class Handler<E, R>(val resolved: (E) -> R,
//                           val rejected: (Exception) -> R,
//                           val thenable: Thenable<R> = Thenable(noop as Executor<R>))
//  private enum class State { PENDING, RESOLVED, REJECTED }
//}
//
//fun main(args: Array<String>) {
//  Thenable<Any> { resolve, reject ->
//    resolve("")
//  }
//}