package app.lounge.gplay

class GplayException(var errorCode: Int, message: String) : Exception(message)