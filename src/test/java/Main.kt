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

    testBasics(expect) { it.split(" ").toTypedArray() }
    testBasics(expect) { "--split \"${it.replace("\"", "\\\"")}".split(" ").toTypedArray() }
}

fun testBasics(expect: Expect, transform: (String) -> Array<String>) {
    val server = mock<MinecraftServer>()
    val sender = mock<ICommandSender>()
    val pos = BlockPos(0, 0, 0)

    val removeQuote: (String) -> String = {
        if (it.startsWith("\""))
            it.substring(1)
        else
            it
    }

    assertSet("Server",
            b = expect.get(server, sender, transform("Server"), pos).map(removeQuote)
    );

    assertSet("Server",
            b = expect.get(server, sender, transform("Serv"), pos).map(removeQuote)
    );

    assertSet("Server", "World",
            b = expect.get(server, sender, transform(""), pos).map(removeQuote)
    );

    // Index

    assertSet("foo", "fee",
            b = expect.get(server, sender, transform("Server f"), pos).map(removeQuote)
    );

    // Name

    assertSet("name1", "name2",
            b = expect.get(server, sender, transform("--name n"), pos).map(removeQuote)
    );

    assertSet("Server", "World",
            b = expect.get(server, sender, transform("--flag "), pos).map(removeQuote)
    );

    // Split

    assertSet("word1", "word2",
            b = expect.get(server, sender, transform("--words \"some thing word"), pos).map(removeQuote)
    );
}

fun expect(e: Expect) = e
        .any("Server", "World")
        .any("foo", "boo", "fee", "bee")
        .named("name").any("name1", "name2")
        .flag("flag")
        .named("words").words { it.any("word1", "word2") }