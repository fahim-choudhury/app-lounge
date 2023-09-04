package foundation.e.apps.data.enums

enum class User {
    NO_GOOGLE,
    ANONYMOUS,
    GOOGLE,
    UNAVAILABLE;

    companion object {
        fun getUser(userString: String): User {
            val userStrings = values().map { it.name }
            return if (userString !in userStrings) {
                UNAVAILABLE
            } else {
                valueOf(userString)
            }
        }
    }
}
