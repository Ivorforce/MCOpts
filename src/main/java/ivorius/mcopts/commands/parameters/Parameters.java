/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.mcopts.commands.parameters;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.primitives.Doubles;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import ivorius.mcopts.MCOpts;
import ivorius.mcopts.commands.parameters.expect.Expect;
import net.minecraft.command.CommandException;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by lukas on 30.05.17.
 */
public class Parameters
{
    public static final String SHORT_FLAG_PREFIX = "-";
    public static final String LONG_FLAG_PREFIX = "--";

    protected List<Pair<String, String>> raw;

    protected Set<String> flags = new HashSet<>();
    protected ListMultimap<String, String> params = ArrayListMultimap.create();
    protected ListMultimap<String, String> rawParams = ArrayListMultimap.create();
    protected List<String> order = new ArrayList<>();

    protected TObjectIntMap<String> restrictFlags = new TObjectIntHashMap<>();
    protected Set<String> declaredFlags = new HashSet<>();
    protected Map<String, String> alias = new HashMap<>();
    protected int until = -1;

    public static Parameters of(String[] args, Function<Parameters, Parameters> c) throws CommandException
    {
        Parameters parameters = new Parameters();
        return (c != null ? c.apply(parameters) : parameters).build(args);
    }

    public static Parameters ofLenient(String[] args, Function<Parameters, Parameters> c)
    {
        Parameters parameters = new Parameters();
        return (c != null ? c.apply(parameters) : parameters).buildLenient(args);
    }

    public static String prefix(boolean isShort)
    {
        return isShort ? SHORT_FLAG_PREFIX : LONG_FLAG_PREFIX;
    }

    public static boolean hasLongPrefix(String name)
    {
        return name.startsWith(LONG_FLAG_PREFIX);
    }

    public static boolean hasShortPrefix(String name)
    {
        return name.startsWith(SHORT_FLAG_PREFIX) && Doubles.tryParse(name) == null;
    }

    public static String[] quoted(String[] args)
    {
        return parse(args).map(Pair::getRight).toArray(String[]::new);
    }

    public static Stream<Pair<String, String>> parse(String[] args)
    {
        String full = String.join(" ", args);
        StringReader reader = new StringReader(full);

        StreamTokenizer tokenizer = new StreamTokenizer(reader);
        tokenizer.resetSyntax();
        tokenizer.wordChars(0, Integer.MAX_VALUE);
        tokenizer.whitespaceChars(0, ' ');
        tokenizer.quoteChar('"');

        List<String> parsed = new ArrayList<>();
        List<String> raw = new ArrayList<>();
        int last_tt = StreamTokenizer.TT_EOF;
        int lastIndex = 0;
        try
        {
            while (tokenizer.nextToken() != StreamTokenizer.TT_EOF)
            {
                int idx = Math.min(index(reader), full.length());

                String arg = tokenizer.sval;
                String argRaw = full.substring(lastIndex, idx);

                parsed.add(arg);
                raw.add(tokenizer.ttype != '\"' ? argRaw.trim() : argRaw);

                lastIndex = idx;
                last_tt = tokenizer.ttype;
            }
        }
        catch (IOException e)
        {
            // Should never happen
            MCOpts.logger.error("Error reading string", e);
        }

        reader.close();

        if (args.length > 0 && args[args.length - 1].length() == 0)
        {
            String lastRaw = raw.size() > 0 ? Iterables.getLast(raw) : null;
            // Are we in an open quote?
            if (!(last_tt == '\"' && (lastRaw == null || lastRaw.charAt(lastRaw.length() - 1) != '\"')))
            {
                // We are currently writing a new param
                parsed.add("");
                raw.add("");
            }
        }

        return IntStream.range(0, parsed.size())
                .mapToObj(i -> Pair.of(raw.get(i), parsed.get(i)));
    }

    public static int index(StringReader reader)
    {
        try
        {
            return ReflectionHelper.findField(StringReader.class, "next").getInt(reader);
        }
        catch (IllegalAccessException e)
        {
            MCOpts.logger.error("Error trying to get next", e);
        }

        return 0;
    }

    @Nonnull
    public static String trimQuotes(String arg)
    {
        String trimmed = arg;
        if (trimmed.startsWith("\""))
            trimmed = trimmed.substring(1, trimmed.length() - (trimmed.length() > 1 && trimmed.endsWith("\"") ? 1 : 0));
        return trimmed;
    }

    public static Expect expect()
    {
        return new Expect();
    }

    public static String escape(String s)
    {
        return s.replaceAll("\"", Matcher.quoteReplacement("\\\""));
    }

    public Parameters build(String[] args) throws CommandException
    {
        return buildRaw(args, false);
    }

    public Parameters buildLenient(String[] args)
    {
        try
        {
            return buildRaw(args, true);
        }
        catch (CommandException e)
        {
            throw new InternalError();
        }
    }

    public Parameters buildRaw(String[] args, boolean lenient) throws CommandException
    {
        raw = new ArrayList<>(parse(args).collect(Collectors.toList()));

        order.add(null);

        String curName = null;
        boolean rawInterpreted = true;
        for (int p = 0; p < raw.size(); p++)
        {
            Pair<String, String> pair = raw.get(p);
            String argRaw = pair.getLeft();
            String arg = pair.getRight();

            if (!interpretes() && rawInterpreted)
            {
                List<Pair<String, String>> rest = raw.subList(p, raw.size());
                String restString = rest.stream().map(Pair::getLeft).reduce(NaP::join).orElse("");
                rest.clear();
                // From CommandHandler limit -1, so we keep empty params
                Arrays.stream(restString.split(" ", -1)).map(s -> Pair.of(s, s)).forEach(raw::add);
                rawInterpreted = false;
                p--; // Do this again but not interpreting
                continue;
            }

            // Test argRaw for params since we don't want --name in "--split \"--name\"" to be a param
            if (interpretes() && hasLongPrefix(argRaw))
            {
                flags.add(curName = root(argRaw.substring(LONG_FLAG_PREFIX.length()).trim()));
                if (!lenient) testAdd(curName);
                if (declaredFlags.contains(curName)) curName = null;
            }
            else if (interpretes() && hasShortPrefix(argRaw))
            {
                List<String> curFlags = argRaw.substring(SHORT_FLAG_PREFIX.length()).trim().chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList());

                for (int i = 0; i < curFlags.size(); i++)
                {
                    flags.add(curName = root(curFlags.get(i)));
                    if (!lenient) testAdd(curName);
                    if (declaredFlags.contains(curName))
                        curName = null;
                    else if (curFlags.size() > i + 1)
                    {
                        // Direct input, e.g. -fusers/foo/file.png
                        String rest = String.join("", curFlags.subList(i + 1, curFlags.size()));
                        order.add(curName);
                        params.put(curName, rest);
                        rawParams.put(curName, argRaw);
                        curName = null;
                    }
                }
            }
            else
            {
                if (!lenient) testAdd(curName);

                if (until > 0 && curName == null) until--;

                order.add(curName);

                params.put(curName, arg);
                rawParams.put(curName, argRaw);

                curName = null;
            }
        }

        return this;
    }

    protected void testAdd(String name) throws CommandException
    {
        if (!restrictFlags.containsKey(name))
            throw ParameterUnknownException.create(name);

        int restrict = restrictFlags.get(name);
        if (!declaredFlags.contains(name) && (restrict >= 0 && params.get(name).size() >= restrict))
            throw ParameterTooManyArgumentsException.create(name);
    }

    public void requireBuilt() throws IllegalStateException
    {
        if (raw == null)
            throw new IllegalStateException();
    }

    public void requireUnbuilt() throws IllegalStateException
    {
        if (raw != null)
            throw new IllegalStateException();
    }

    public Parameters alias(String parent, String... aliases)
    {
        requireUnbuilt();
        parent = root(parent);

        for (String alias : aliases)
            this.alias.put(alias, parent);

        if (flags.removeAll(Arrays.asList(aliases)))
            flags.add(parent);

        return this;
    }

    public Parameters flag(String flag, String... aliases)
    {
        requireUnbuilt();
        declaredFlags.add(root(flag));
        alias(flag, aliases);
        return this;
    }

    public Parameters flags(Collection<String> flags)
    {
        for (String flag : flags)
            flag(flag);
        return this;
    }

    public Parameters flags(String... flags)
    {
        return flags(Arrays.asList(flags));
    }

    /**
     * Required for things like /execute.
     * Everything past this ordered argument will not be treated as a named parameter.
     */
    public Parameters until(int until)
    {
        this.until = until;
        return this;
    }

    public Parameters restrict(TObjectIntMap<String> flags)
    {
        restrictFlags.putAll(flags);
        return this;
    }

    public String root(String name)
    {
        String other;
        while ((other = alias.get(name)) != null)
            name = other;
        return name;
    }

    public String lastName()
    {
        return Iterables.getLast(order);
    }

    public String last()
    {
        requireBuilt();
        return Iterables.getLast(raw).getRight();
    }

    public String lastRaw()
    {
        requireBuilt();
        return Iterables.getLast(raw).getLeft();
    }

    public String[] lastAsArray()
    {
        return new String[]{last()};
    }

    public boolean interpretes()
    {
        return until != 0;
    }

    public <T> Parameter<T> get(Function<Parameters, Parameter<T>> fun)
    {
        return fun.apply(this);
    }

    public Map<String, Parameter> entries()
    {
        requireBuilt();
        return flags.stream().collect(Collectors.toMap(k -> k,
                k -> new Parameter<String>(0, k, params.get(k), null)));
    }

    public boolean has(@Nonnull String flag)
    {
        requireBuilt();
        return flags.contains(root(flag));
    }

    public Parameter<String> get(int idx)
    {
        requireBuilt();
        return new Parameter<String>(0, null, params.get(null), null).move(idx);
    }

    public Parameter<String> get(@Nonnull String name)
    {
        requireBuilt();
        name = root(name);
        return new Parameter<>(!has(name) && !params.containsKey(name) ? -1 : 0, name, params.get(name), null);
    }

    public List<Pair<String, String>> raw()
    {
        requireBuilt();
        return Collections.unmodifiableList(raw);
    }

    public Parameter<String> raw(int idx)
    {
        requireBuilt();
        return new Parameter<String>(0, null, rawParams.get(null), null).move(idx);
    }

    public Parameter<String> raw(@Nonnull String name)
    {
        requireBuilt();
        name = root(name);
        return new Parameter<>(!has(name) && !rawParams.containsKey(name) ? -1 : 0, name, params.get(name), null);
    }

    public static class ParameterUnknownException extends CommandException
    {
        private String parameter;

        private ParameterUnknownException(String message, Object... objects)
        {
            super(message, objects);
        }

        public static ParameterUnknownException create(String name)
        {
            ParameterUnknownException exc = MCOpts.translations.object(ParameterUnknownException::new, "commands.parameters.unknown", name);
            exc.parameter = name;
            return exc;
        }

        public String getParameter()
        {
            return parameter;
        }
    }

    public static class ParameterTooManyArgumentsException extends CommandException
    {
        private String parameter;

        private ParameterTooManyArgumentsException(String message, Object... objects)
        {
            super(message, objects);
        }

        public static ParameterTooManyArgumentsException create(String name)
        {
            ParameterTooManyArgumentsException exc = name != null
                    ? MCOpts.translations.object(ParameterTooManyArgumentsException::new, "commands.parameters.toomany.named", name)
                    : MCOpts.translations.object(ParameterTooManyArgumentsException::new, "commands.parameters.toomany.ordered");
            exc.parameter = name;
            return exc;
        }

        public String getParameter()
        {
            return parameter;
        }
    }
}
