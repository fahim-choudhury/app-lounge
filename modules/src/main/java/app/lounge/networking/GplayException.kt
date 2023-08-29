package app.lounge.networking

class GplayException(var errorCode: Int, message: String) : Exception(message)