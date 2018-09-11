// !LANGUAGE: +InlineClasses

annotation class Ann

inline class Z(val x: Int)

class Test @Ann constructor(@Ann val z: Z) {
    @Ann constructor(z: Z, @Ann a: Int) : this(Z(z.x + a)) {}
}