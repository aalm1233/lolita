import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.data.remote.SeedreamImageApi
import com.lolita.app.domain.model.ImageGenerationFailure
import com.lolita.app.domain.model.MannequinPresentation
import com.lolita.app.domain.model.MannequinRequest
import com.lolita.app.domain.model.MannequinSubject
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.ItemComposition
import com.lolita.app.domain.model.StandaloneProductPrompts
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path

/** Evaluation entry point: production prompt and freshly compiled production transport.
 * Host-normalized inputs are deliberate; this does not test Android image encoding or UI.
 * Python owns the independent 500-call ledger and reserves before invoking `run`.
 */
fun main(args: Array<String>) = runBlocking {
    val folder = Path.of(args[1])
    val job = JsonParser.parseString(Files.readString(folder.resolve("manifest.json"))).asJsonObject
    val paths = job["inputs"].asJsonArray.map { Path.of(it.asString) }
    val subject = MannequinSubject(1, job["category"].asString,
        MannequinPresentation.valueOf(job["presentation"].asString),
        CategorySemantic.valueOf(job["semantic"].asString))
    val instructions = job["instructions"]?.asString.orEmpty()
    val composition = job["composition"]?.asString?.let(ItemComposition::valueOf) ?: ItemComposition.UNCONFIRMED
    val colors = job["colors"]?.asJsonArray?.map { it.asString }.orEmpty()
    val prompt = if (subject.presentation == MannequinPresentation.ACCESSORY)
        StandaloneProductPrompts.front(requireNotNull(subject.semantic), composition, job["name"].asString, instructions, colors)
    else MannequinRequest(1, paths.last().toString(), instructions, subject).prompt()
    if (args[0] == "prepare") {
        Files.writeString(folder.resolve("prompt.txt"), prompt)
        println("Prepared production prompt; no HTTP")
        return@runBlocking
    }
    require(args[0] == "run")
    val keys = JsonParser.parseString(Files.readString(Path.of(args[2]))).asJsonObject
    val key = keys["seedream_api_key_if_different"]?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        .ifBlank { keys["ark_api_key"].asString.trim() }
    var connections = 0
    try {
        val api = SeedreamImageApi { url ->
            require(url.toString() == SeedreamImageApi.ENDPOINT && connections++ == 0)
            url.openConnection() as java.net.HttpURLConnection
        }
        val bytes = withTimeout(330000) { api.generateMannequin(key, paths.map(Files::readAllBytes), prompt) }
        Files.write(folder.resolve("output.png"), bytes)
        println(Gson().toJson(mapOf("status" to "SUCCESS", "connections" to connections)))
    } catch (e: Exception) {
        println(Gson().toJson(mapOf("status" to "FAILED", "connections" to connections,
            "failure" to if (e is ImageGenerationFailure) e.kind.name else e.javaClass.simpleName)))
    }
}
