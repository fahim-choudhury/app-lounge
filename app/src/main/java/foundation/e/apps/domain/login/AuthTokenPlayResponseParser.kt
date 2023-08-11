package foundation.e.apps.domain.login

import java.util.StringTokenizer

object AuthTokenPlayResponseParser {
    fun parseResponse(response: String?): Map<String, String> {
        val keyValueMap: MutableMap<String, String> = HashMap()
        val st = StringTokenizer(response, "\n\r")
        while (st.hasMoreTokens()) {
            val keyValue = st.nextToken().split("=")
            if (keyValue.size >= 2) {
                keyValueMap[keyValue[0]] = keyValue[1]
            }
        }
        return keyValueMap
    }
}