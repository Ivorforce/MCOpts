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

    assertSet("Server",
            b = expect.get(server, sender, transform("Server"), pos).map(completionTransform)
    );

    assertSet("Server",
            b = expect.get(server, sender, transform("Serv"), pos).map(completionTransform)
    );

    assertSet("Server",
            b = expect.get(server, sender, transform("serv"), pos).map(completionTransform)
    );

    assertSet("Server", "World",
            b = expect.get(server, sender, transform(""), pos).map(completionTransform)
    );

    // Index

    assertSet("foo", "fee",
            b = expect.get(server, sender, transform("Server f"), pos).map(completionTransform)
    );

    // Name

    assertSet("name1", "name2",
            b = expect.get(server, sender, transform("--name n"), pos).map(completionTransform)
    );

    assertSet("Server", "World",
            b = expect.get(server, sender, transform("--flag "), pos).map(completionTransform)
    );

    // Split

    assertSet("word1", "word2",
            b = expect.get(server, sender, transform("--words \"some thing word"), pos).map(completionTransform)
    );

    assertSet("word1", "word2",
            b = expect.get(server, sender, transform("--words \"some thing word"), pos).map(completionTransform)
    );

    // Interpret

    // We'd expect quotes at the start but it's easier this way
    assertSet("\"int1", "\"int3\"",
            b = expect.get(server, sender, transform("Server foo \""), pos).map(completionTransform)
    );

    // Long

    assertSet("\"This has spaces",
            b = expect.get(server, sender, transform("--spaces \"This"), pos).map(completionTransform)
    );

    assertSet("has spaces",
            b = expect.get(server, sender, transform("--spaces \"This has"), pos).map(completionTransform)
    );

    assertSet("this has: \\\"too\\\"",
            b = expect.get(server, sender, transform("--spaces \"And this"), pos).map(completionTransform)
    );

    // Suggest

    assertSet("Server", "World",
            b = expect.get(server, sender, transform("--suggest Server"), pos).map(completionTransform)
    );
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
        .named("suggest").nextRaw({ server, sender, parameters, pos -> listOf("Server", "World") })