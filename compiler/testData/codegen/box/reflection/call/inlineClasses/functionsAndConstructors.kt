// !LANGUAGE: +InlineClasses
// IGNORE_BACKEND: JS_IR, JS, NATIVE, JVM_IR
// WITH_REFLECT

import kotlin.test.assertEquals

inline class S(val value: String) {
    operator fun plus(other: S): S = S(this.value + other.value)
}

class C {
    fun member(x: S, y: String): S = x + S(y)
}

fun topLevel(x: String, y: S): S = S(x) + y

/* TODO: support constructors with inline class types in the signature
class D {
    inner class Inner(x: S, y: S) {
        val result = x + y
    }
}
*/

fun S.extension(y: S): S = this + y

fun box(): String {
    assertEquals(S("ab"), C::member.call(C(), S("a"), "b"))
    assertEquals(S("cd"), ::topLevel.call("c", S("d")))
    // assertEquals(S("ef"), D::Inner.call(D(), S("e"), S("f")).result)
    assertEquals(S("gh"), S::extension.call(S("g"), S("h")))

    assertEquals(S("ij"), C()::member.call(S("i"), "j"))
    // assertEquals(S("kl"), D()::Inner.call(S("k"), S("l")).result)
    assertEquals(S("mn"), S("m")::extension.call(S("n")))

    return "OK"
}
