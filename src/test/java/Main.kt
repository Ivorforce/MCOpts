import com.google.common.collect.Sets
import ivorius.mcopts.commands.parameters.Parameters
import ivorius.mcopts.commands.parameters.expect.Expect
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import org.mockito.Mockito

/**
 * Created by lukas on 15.06.17.
 */
inline fun <reified T : Any> mock(): T = Mockito.mock(T::class.java)

inline fun assertEquals(a: Any, b: Any) = assert(a == b) { "$b is not $a" }

inline fun assertSet(vararg a: Any, b: Collection<*>) = assertEquals(setOf(*a), Sets.newHashSet(b))

fun main(args: Array<String>) {

    // Words
    val expect = expect(Parameters.expect())
            .named("split").split { expect(it) }

    testBasics(expect, { it.split(" ").toTypedArray() }) { it }
    testBasics(expect, { "--split \"${it.replace("\"", "\\\"")}".split(" ").toTypedArray() }, {
        (if (it.startsWith("\"")) it.substring(1) else it).replace("\\\"", "\"")
    })
}

fun testBasics(expect: Expect, transform: (String) -> Array<String>, completionTransform: (String) -> String) {
    val server = mock<MinecraftServer>()
    val sender = mock<ICommandSender>()
    val pos = BlockPos(0, 0, 0)

    val from : (String) -> List<String> = { expect.get(server, sender, transform(it), pos).map(completionTransform) };

    assertSet("Server", b = from("Server"));

    assertSet("Server", b = from("Serv"));

    assertSet("Server", b = from("serv"));

    assertSet("Server", "World", b = from(""));

    // Index

    assertSet("foo", "fee", b = from("Server f"));

    // Name

    assertSet("name1", "name2", b = from("--name n"));

    assertSet("Server", "World", b = from("--flag "));

    // Split

    assertSet("word1", "word2", b = from("--words \"some thing word"));

    assertSet("word1", "word2", b = from("--words \"some thing word"));

    // Interpret

    // We'd expect quotes at the start but it's easier this way
    assertSet("\"int1", "\"int3\"", b = from("Server foo \""));

    // Long

    assertSet("\"This has spaces", b = from("--spaces \"This"));

    assertSet("has spaces", b = from("--spaces \"This has"));

    assertSet("this has: \\\"too\\\"", b = from("--spaces \"And this"));

    // Suggest

    assertSet("Server", "World", b = from("--suggest Server"));
}

fun expect(e: Expect) = e
        .any("Server", "World")
        .any("foo", "boo", "fee", "bee")
        .stopInterpreting()
        .any("\"int1", "int2\"", "\"int3\"")
        .named("name").any("name1", "name2")
        .flag("flag")
        .named("words").words { it.any("word1", "word2") }
        .named("spaces").any("This has spaces", "And this has: \"too\"")
        .named("suggest").anyRaw("Server", "World")
