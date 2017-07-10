import com.google.common.collect.Sets
import ivorius.mcopts.commands.parameters.Parameter
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

fun assertThrows(a: () -> Unit, b: Class<*>) {
    try {
        a.invoke();
    }
    catch (e: Exception){
        if (b.isAssignableFrom(e.javaClass))
            return;

        throw AssertionError("function throwed $e instead of $b");
    }

    throw AssertionError("function did not throw " + b);
}

fun main(args: Array<String>) {

    // Words
    val expect = expect(Parameters.expect())
            .named("split").split { expect(it) }

    val transformBase: (String) -> Array<String> = { it.split(" ").toTypedArray() }
    val transformInner: (String) -> Array<String> = { "--split \"${it.replace("\"", "\\\"")}".split(" ").toTypedArray() }

    testExpect(expect, transformBase) { it }
    testExpect(expect, transformInner, {
        (if (it.startsWith("\"")) it.substring(1) else it).replace("\\\"", "\"")
    })

    testParameters({ Parameters.of(transformBase(it), { expect.declare(it) }) });
    testParameters({ Parameters.of(Parameters.of(transformInner(it), { expect.declare(it) })["split"].varargs({ arrayOfNulls<String>(it) }).get(), { expect.declare(it) }) });
}

fun testExpect(expect: Expect, transform: (String) -> Array<String>, completionTransform: (String) -> String) {
    val server = mock<MinecraftServer>()
    val sender = mock<ICommandSender>()
    val pos = BlockPos(0, 0, 0)

    val from : (String) -> List<String> = { expect.get(server, sender, transform(it), pos).map(completionTransform) };

    assertSet("Server", b = from("Server"))
    assertSet("Server", b = from("Serv"))
    assertSet("Server", b = from("serv"))
    assertSet("Server", "World", b = from(""))

    // Index

    assertSet("foo", "fee", b = from("Server f"))

    // Name

    assertSet("name1", "name2", b = from("--name n"))
    assertSet("Server", "World", b = from("--flag "))

    // Repeat

    assertSet("param2", b = from("--rep a --rep b --rep "))

    // Split

    assertSet("word1", "word2", b = from("--words \"some thing word"))
    assertSet("word1", "word2", b = from("--words \"some thing word"))

    // Interpret

    // We'd expect quotes at the start but it's easier this way
    assertSet("\"int1", "\"int3\"", b = from("Server foo \""))

    // Long

    assertSet("\"This has spaces", b = from("--spaces \"This"))
    assertSet("has spaces", b = from("--spaces \"This has"))
    assertSet("this has: \\\"too\\\"", b = from("--spaces \"And this"))

    // Suggest

    assertSet("Server", "World", b = from("--suggest Server"))

    // Or

    assertSet("foo", "fee", b = from("--or f"))
}

fun testParameters(transform: (String) -> Parameters) {
    val from : (String) -> Parameters = { transform(it) };

    val extract : (Parameter<String>) -> String = { it.optional().orElse(null) }

    // Index

    assertEquals(extract(from("Server")[0]), "Server")
    assertEquals(extract(from("Test")[0]), "Test")

    // Has

    assertEquals(from("Server foo")[0].has(2), true)
    assertEquals(from("Server foo")[0].has(3), false)

    // Move

    assertEquals(extract(from("Server foo")[0].move(1)), "foo")
    assertEquals(from("Server foo")[0].move(2).has(1), false)

    // Flag

    assertEquals(from("--flag").has("flag"), true)
    assertEquals(from("Test --flag").has("flag"), true)
    assertEquals(from("--flag Test").has("flag"), true)

    assertEquals(from("Server --flag")["flag"].isSet, true)
    assertEquals(from("Server")["flag"].isSet, false)

    assertEquals(from("-f").has("flag"), true)

    // Named

    assertEquals(extract(from("--name name1")["name"]), "name1")
    assertEquals(extract(from("--name Test")["name"]), "Test")

    // Unknown

    assertThrows({ from("--asjdkla") }, Parameters.ParameterUnknownException::class.java)
    assertThrows({ from("-fs") }, Parameters.ParameterUnknownException::class.java)

    assertThrows({ from("--name a --name b") }, Parameters.ParameterTooManyArgumentsException::class.java)
    from("--rep a --rep b --rep c") // Don't throw because repeat()
}

fun expect(e: Expect) = e
        .any("Server", "World")
        .any("foo", "boo", "fee", "bee")
        .stopInterpreting()
        .any("\"int1", "int2\"", "\"int3\"")
        .named("name").any("name1", "name2")
        .named("rep").any("param1").any("param2").repeat()
        .flag("flag", "f")
        .named("words").words { it.any("word1", "word2") }
        .named("spaces").any("This has spaces", "And this has: \"too\"")
        .named("suggest").anyRaw("Server", "World")
        .named("or").any("foo", "boo").or().any("fee", "bee")
