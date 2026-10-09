package game.harness

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.apache.logging.log4j.LogManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class HarnessRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String = "",
)

data class HarnessResponse(val status: Int, val body: Any)

/** A request the harness refuses; [status] is the HTTP status sent back with [message]. */
class HarnessException(val status: Int, override val message: String) : RuntimeException(message)

data class ErrorView(val error: String)

class Route(val method: String, val pattern: String, val handler: (RouteCall) -> Any) {

    private val segments = pathSegments(pattern)

    /** Path parameters when [path] fits this route's pattern, `null` otherwise. */
    fun match(path: String): Map<String, String>? {
        val parts = pathSegments(path)
        if (parts.size != segments.size) {
            return null
        }
        val params = mutableMapOf<String, String>()
        for ((segment, part) in segments.zip(parts)) {
            when {
                isParameter(segment) -> params[segment.substring(1, segment.length - 1)] = part
                segment != part -> return null
            }
        }
        return params
    }

    private fun isParameter(segment: String) = segment.startsWith("{") && segment.endsWith("}")
}

class RouteCall(val request: HarnessRequest, private val pathParams: Map<String, String>) {

    fun path(name: String): String =
        checkNotNull(pathParams[name]) { "route has no path parameter '$name'" }

    fun queryInt(name: String, default: Int): Int {
        val value = request.query[name] ?: return default
        return value.toIntOrNull() ?: throw HarnessException(400, "query parameter '$name' must be an integer")
    }

    fun queryLong(name: String, default: Long): Long {
        val value = request.query[name] ?: return default
        return value.toLongOrNull() ?: throw HarnessException(400, "query parameter '$name' must be an integer")
    }

    fun queryBoolean(name: String, default: Boolean): Boolean =
        when (request.query[name]?.lowercase()) {
            null -> default
            "true", "1", "" -> true
            "false", "0" -> false
            else -> throw HarnessException(400, "query parameter '$name' must be true or false")
        }

    fun body(): RequestBody {
        if (request.body.isBlank()) {
            throw HarnessException(400, "this endpoint needs a JSON object body")
        }
        val element = JsonParser.parseString(request.body)
        if (!element.isJsonObject) {
            throw HarnessException(400, "the body must be a JSON object")
        }
        return RequestBody(element.asJsonObject)
    }
}

/** Typed access to a JSON object body, answering 400 with the field name when a field is missing or mistyped. */
class RequestBody(private val json: JsonObject) {

    fun int(name: String): Int = intOrNull(name) ?: throw missing(name, "an integer")

    fun int(name: String, default: Int): Int = intOrNull(name) ?: default

    fun string(name: String): String = stringOrNull(name) ?: throw missing(name, "a string")

    fun string(name: String, default: String): String = stringOrNull(name) ?: default

    /** An array of JSON objects; a missing array is required, an empty one is fine. */
    fun objects(name: String): List<RequestBody> {
        val element = json.get(name)?.takeIf { !it.isJsonNull } ?: throw missing(name, "an array of objects")
        if (!element.isJsonArray || element.asJsonArray.any { !it.isJsonObject }) {
            throw HarnessException(400, "field '$name' must be an array of objects")
        }
        return element.asJsonArray.map { RequestBody(it.asJsonObject) }
    }

    /** An object of single values read as text (`5` and `"5"` alike); a missing object is empty. */
    fun strings(name: String): Map<String, String> {
        val element = json.get(name)?.takeIf { !it.isJsonNull } ?: return emptyMap()
        if (!element.isJsonObject || element.asJsonObject.entrySet().any { !it.value.isJsonPrimitive }) {
            throw HarnessException(400, "field '$name' must be an object of single values")
        }
        return element.asJsonObject.entrySet().associate { (key, value) -> key to value.asString }
    }

    fun intOrNull(name: String): Int? {
        val primitive = primitiveOrNull(name) ?: return null
        if (!primitive.isNumber || primitive.asDouble != primitive.asInt.toDouble()) {
            throw HarnessException(400, "field '$name' must be an integer")
        }
        return primitive.asInt
    }

    private fun stringOrNull(name: String): String? {
        val primitive = primitiveOrNull(name) ?: return null
        if (!primitive.isString) {
            throw HarnessException(400, "field '$name' must be a string")
        }
        return primitive.asString
    }

    private fun primitiveOrNull(name: String): JsonPrimitive? {
        val element: JsonElement = json.get(name) ?: return null
        if (element.isJsonNull) {
            return null
        }
        if (!element.isJsonPrimitive) {
            throw HarnessException(400, "field '$name' must be a single value")
        }
        return element.asJsonPrimitive
    }

    private fun missing(name: String, type: String) =
        HarnessException(400, "field '$name' is required and must be $type")
}

/** Maps a request to the first route whose pattern and method fit, and every failure to a JSON error answer. */
class HarnessRouter(private val routes: List<Route>) {

    fun handle(request: HarnessRequest): HarnessResponse {
        val candidates = routes.mapNotNull { route -> route.match(request.path)?.let { route to it } }
        if (candidates.isEmpty()) {
            return failure(404, "no endpoint at ${request.path}")
        }
        val (route, params) = candidates.firstOrNull { (route, _) -> route.method == request.method }
            ?: return failure(405, "${request.path} accepts ${candidates.joinToString { it.first.method }}")
        return try {
            HarnessResponse(200, route.handler(RouteCall(request, params)))
        } catch (e: HarnessException) {
            failure(e.status, e.message)
        } catch (e: JsonParseException) {
            failure(400, "the body is not valid JSON: ${e.message}")
        } catch (e: Exception) {
            logger.error("Harness request {} {} failed.", request.method, request.path, e)
            failure(500, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun failure(status: Int, message: String) = HarnessResponse(status, ErrorView(message))

    private companion object {
        val logger = LogManager.getLogger(HarnessRouter::class.java)
    }
}

/** Decodes a raw `a=1&b=two` query string; a key without `=` maps to an empty value. */
fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrEmpty()) {
        return emptyMap()
    }
    return rawQuery.split("&").filter { it.isNotEmpty() }.associate { pair ->
        val key = pair.substringBefore("=")
        val value = pair.substringAfter("=", missingDelimiterValue = "")
        decode(key) to decode(value)
    }
}

private fun decode(text: String): String = URLDecoder.decode(text, StandardCharsets.UTF_8)

private fun pathSegments(path: String): List<String> = path.split("/").filter { it.isNotEmpty() }
