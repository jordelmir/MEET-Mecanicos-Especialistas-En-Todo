import io.elysium.safety.CustodyEventV2
import java.io.File

/** Fixture fields passed as UTF-8 lines to avoid a runtime JSON dependency. */
fun main(args: Array<String>) {
    val fields = File(args.single()).readLines(Charsets.UTF_8)
    require(fields.size == 10)
    val event = CustodyEventV2(fields[0],fields[1],fields[2].takeIf { it.isNotEmpty() },
        fields[3],fields[4],fields[5],fields[6].takeIf { it.isNotEmpty() },fields[7])
    val actual = event.sha256()
    check(actual == fields[8]) { "Safety custody Kotlin parity failed" }
    check(event.canonical() == fields[9]) { "Safety custody canonical bytes differ" }
    println(actual)
}
