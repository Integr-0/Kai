package net.integr.kai.output

enum class GenericStatus {
    WAITING,
    STARTED,
    FINISHED;

    fun next(): GenericStatus {
        return when (this) {
            WAITING -> STARTED
            STARTED -> FINISHED
            FINISHED -> FINISHED
        }
    }

    fun isFinished(): Boolean {
        return this == FINISHED
    }

    fun isStarted(): Boolean {
        return this == STARTED
    }

    fun moveIfStarted(call: () -> Unit): GenericStatus {
        return if (this == STARTED) {
            call()
            FINISHED
        } else this
    }


    fun isWaiting(): Boolean {
        return this == WAITING
    }

    fun moveIfWaiting(call: () -> Unit): GenericStatus {
        return if (this == WAITING) {
            call()
            STARTED
        } else this
    }
}