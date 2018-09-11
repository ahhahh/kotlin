val lock = Any()

inline fun inlineMe(c: () -> Unit) {
    synchronized(lock) {
        c()
    }
}
