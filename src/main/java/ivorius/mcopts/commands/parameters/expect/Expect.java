/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.mcopts.commands.parameters.expect;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import ivorius.mcopts.commands.parameters.NaP;
import ivorius.mcopts.commands.parameters.Parameter;
import ivorius.mcopts.commands.parameters.Parameters;
import ivorius.mcopts.translation.Translations;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by lukas on 30.05.17.
 */
public class Expect
{
    protected final Map<String, SuggestParameter> params = new HashMap<>();
    protected final Multimap<String, String> aliases = HashMultimap.create();
    protected final Set<String> shortParams = new HashSet<>();
    protected final Set<String> flags = new HashSet<>();

    protected String currentName;
    protected final List<String> order = new ArrayList<>();
    protected int currentCount;
    protected int until = -1;

    protected boolean or;

    public Expect()
    {
        getOrCreate(null);
    }

    public static Stream<?> unwrap(Object arg)
    {
        // Especially for or() calls
        if (arg instanceof Pair)
            arg = Stream.concat(unwrap(((Pair) arg).getLeft()),
                    unwrap(((Pair) arg).getRight()));
        while (arg instanceof Optional)
            //noinspection unchecked
            arg = ((Optional) arg).orElse(Collections.emptyList());
        if (arg instanceof IntStream)
            arg = ((IntStream) arg).mapToObj(String::valueOf);
        if (arg instanceof Collection<?>)
            arg = ((Collection) arg).stream();
        if (arg instanceof Stream<?>)
            return (Stream<?>) arg;
        return Stream.of(arg);
    }

    public static Collection<String> toStrings(Object arg)
    {
        return unwrap(arg).map(Object::toString).collect(Collectors.toList());
    }

    public static List<String> matching(String arg, Object completion)
    {
        return CommandBase.getListOfStringsMatchingLastWord(new String[]{arg}, unwrap(completion).collect(Collectors.toList()));
    }

    public static List<String> matchingAny(String arg, Object... suggest)
    {
        return CommandBase.getListOfStringsMatchingLastWord(new String[]{arg}, Arrays.asList(suggest));
    }

    public Parameters declareLenient(Parameters parameters)
    {
        parameters.flags(flags);
        this.params.forEach((key, param) ->
        {
            if (!Objects.equals(param.name, key))
                parameters.alias(param.name, key);
        });
        parameters.until(until);
        return parameters;
    }

    public Parameters declare(Parameters parameters)
    {
        TObjectIntMap<String> restrict = new TObjectIntHashMap<>();

        this.params.forEach((key, param) ->
        {
            if (Objects.equals(param.name, key))
                restrict.put(key, param.repeat ? -1 : param.completions.size());
        });

        parameters.restrict(restrict);

        return declareLenient(parameters);
    }

    @Nonnull
    protected SuggestParameter getOrCreate(@Nullable String name)
    {
        SuggestParameter param = params.get(name);
        if (param == null)
            params.put(name, param = new SuggestParameter(name));
        return param;
    }

    public Expect named(@Nonnull String name, String... aliases)
    {
        Pair<String, Boolean> p = name(name);

        SuggestParameter param = getOrCreate(currentName = p.getKey());
        if (p.getRight()) shortParams.add(p.getKey());

        for (String alias : aliases)
        {
            p = name(alias);
            params.put(alias, param);
            if (p.getRight()) shortParams.add(p.getKey());
        }
        this.aliases.putAll(name, Arrays.asList(aliases));

        return this;
    }

    private Pair<String, Boolean> name(String name)
    {
        boolean isShort = false;
        if (name.startsWith(Parameters.LONG_FLAG_PREFIX))
            name = name.substring(Parameters.LONG_FLAG_PREFIX.length());
        else if (name.length() == 1)
            isShort = true;
        else if (name.startsWith(Parameters.SHORT_FLAG_PREFIX))
            throw new IllegalArgumentException();

        return Pair.of(name, isShort);
    }

    public Expect flag(@Nonnull String name, String... aliases)
    {
        flags.add(name);
        Collections.addAll(flags, aliases);
        return named(name, aliases);
    }

    public Expect atOnce(int num)
    {
        currentCount = num;
        return this;
    }

    public Expect nextRaw(Completer completion)
    {
        SuggestParameter cur = getOrCreate(currentName);

        if (or)
        {
            cur.or(completion);
            or = false;
        }
        else
        {
            order.add(currentName);
            cur.next(completion);

            currentCount = 1;
            optional(); // All params are expected to be optional by default
        }

        return this;
    }

    public Expect next(Completer completion)
    {
        return nextRaw((server, sender, params, pos) -> matching(params.last(), completion.complete(server, sender, params, pos)));
    }

    public Expect skip()
    {
        return nextRaw((server, sender, parameters, pos) -> Stream.of());
    }

    public Expect anyRaw(Object... completion)
    {
        return nextRaw((server, sender, params, pos) -> Stream.of(completion));
    }

    public Expect any(Object... completion)
    {
        return nextRaw((server, sender, params, pos) -> matchingAny(params.last(), completion));
    }

    public Expect nextRaw(Object completion)
    {
        return nextRaw((server, sender, params, pos) -> completion);
    }

    public Expect next(Object completion)
    {
        return nextRaw((server, sender, params, pos) -> matching(params.last(), completion));
    }

    public Expect nextRaw(Function<Parameters, ?> completion)
    {
        return nextRaw((server, sender, params, pos) -> completion.apply(params));
    }

    public Expect next(Function<Parameters, ?> completion)
    {
        return nextRaw((server, sender, params, pos) -> matching(params.last(), completion.apply(params)));
    }

    public Expect then(Consumer<Expect> fun)
    {
        fun.accept(this);
        return this;
    }

    public <P> Expect then(BiConsumer<Expect, P> fun, P p)
    {
        fun.accept(this, p);
        return this;
    }

    public Expect repeat()
    {
        SuggestParameter cur = params.get(this.currentName);
        if (cur == null) throw new IllegalStateException();
        cur.repeat = true;
        return this;
    }

    public Expect splitInner(Consumer<Expect> consumer)
    {
        Expect inner = Parameters.expect().then(consumer);

        nextRaw((server, sender, parameters, pos) ->
                {
                    // From CommandHandler
                    String last = parameters.last();
                    String[] split = last.split(" ", -1);

                    int lastSplit = last.lastIndexOf(" ");
                    String lastStart = lastSplit >= 0 ? last.substring(0, lastSplit) + " " : "";

                    return inner
                            .get(server, sender, split, pos)
                            .stream()
                            .map(s -> lastStart + s)
                            ;
                }
        );

        return inner;
    }

    /**
     * For quoted arguments with spaces
     */
    public Expect split(Consumer<Expect> consumer)
    {
        splitInner(consumer);
        return this;
    }

    /**
     * For quoted arguments with spaces that repeat just one completion
     */
    public Expect words(Consumer<Expect> consumer)
    {
        Expect inner = splitInner(expect -> expect.then(consumer).repeat());
        descriptionU(Iterables.getLast(inner.mapLastDescriptions((i, s) -> s)));
        return this;
    }

    public Expect or()
    {
        if (or) throw new IllegalStateException();
        or = true;
        return this;
    }

    public int index()
    {
        return params.size();
    }

    public Expect stopInterpreting()
    {
        until = params.get(null).completions.size();
        return this;
    }

    public List<String> get(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos)
    {
        Parameters parameters = Parameters.ofLenient(args, this::declare);

        String lastName = parameters.lastName();
        Parameter entered = lastName != null ? parameters.get(lastName) : parameters.get(0);
        SuggestParameter param = this.params.get(lastName);

        String currentArg = parameters.last();
        String currentArgRaw = parameters.lastRaw();

        boolean lastArgStartsQuote = args[args.length - 1].startsWith("\"") && parameters.interpretes();
        boolean lastArgQuoted = currentArgRaw.startsWith("\"") && parameters.interpretes();

        boolean longFlag = Parameters.hasLongPrefix(currentArgRaw) && parameters.interpretes();
        boolean shortFlag = Parameters.hasShortPrefix(currentArgRaw) && parameters.interpretes();

        if (param != null && (entered.count() <= param.completions.size() || param.repeat)
                // It notices we are entering a parameter so it won't be added to the parameters args anyway
                && !(parameters.interpretes() && (longFlag || shortFlag)))
        {
            Completer completer = param.completions.get(Math.min(entered.count() - 1, param.completions.size() - 1));
            return toStrings(completer.complete(server, sender, parameters, pos)).stream()
                    // Filter those that match
                    .map(s ->
                    {
                        // If the string starts with this we can back-complete, otherwise it's a 'new suggestion'
                        int wordStartIndex = s.lastIndexOf(' ',
                                CommandBase.doesStringStartWith(currentArg, s) ? currentArg.length() - 1 : s.length());
                        // Is quoted param, so escape contained quotes
                        boolean startQuote = (s.contains(" ") && wordStartIndex < 0) || lastArgStartsQuote;

                        // Take the substring, i.e. the portion we complete, up until the start of the word
                        // We can't just keep the whole thing in case we had spaces before
                        // args[args.length - 1] + s.substring(currentArg.length(), s.length())
                        s = s.substring(wordStartIndex + 1, s.length());

                        if (lastArgQuoted || startQuote)
                            s = Parameters.escape(s);
                        if (startQuote)
                            s = "\"" + s;

                        return s;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (!parameters.interpretes())
            return Collections.emptyList();

        List<String> suggest = new ArrayList<>();
        suggest.addAll(remaining(currentArg, parameters, false));
        suggest.addAll(remaining(currentArg, parameters, true));
        return matching(currentArg, suggest);
    }

    @Nonnull
    public Collection<String> remaining(String currentArg, Parameters parameters, boolean useShort)
    {
        return toStrings(this.params.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .filter(e -> !parameters.has(e.getKey()) // For flags
                        || parameters.get(e.getKey()).count() < e.getValue().completions.size() || e.getValue().repeat)
                .map(Map.Entry::getKey)
                .filter(p -> shortParams.contains(p) == useShort)
                .map(p -> Parameters.prefix(shortParams.contains(p)) + p)
        );
    }

    protected List<String> mapLastDescriptions(BiFunction<Integer, String, String> fun)
    {
        List<String> relevant = order.subList(order.size() - currentCount, order.size());
        int[] idx = new int[1];
        List<String> last = relevant.stream().map(s ->
        {
            List<String> list = params.get(s).descriptions;
            return list.remove(list.size() - 1);
        }).map(s -> fun.apply(idx[0]++, s)).collect(Collectors.toList()); // Collect since we want to remove all first and then add back
        relevant.stream().forEach(s ->
                params.get(s).descriptions.add(last.remove(last.size() - 1))
        );
        return relevant;
    }

    protected String stripOptionality(String description)
    {
        return description.startsWith("[") || description.startsWith("<")
                ? description.substring(1, description.length() - 1) : description;
    }

    protected String preserveOptionality(String description, String ref)
    {
        return ref.startsWith("[") ? String.format("[%s]", description)
                : ref.startsWith("<") ? String.format("<%s>", description)
                : description;
    }

    /**
     * Useful only for usage()
     */
    public Expect descriptionU(List<String> description)
    {
        if (description.size() != currentCount)
            throw new IllegalArgumentException();
        mapLastDescriptions((i, s) -> preserveOptionality(description.get(i), s));
        return this;
    }

    public Expect descriptionU(String... descriptions)
    {
        return descriptionU(Arrays.asList(descriptions));
    }

    public Expect description(String... keys)
    {
        return descriptionU(Arrays.stream(keys)
                .map(Translations::get)
                .collect(Collectors.toList()));
    }

    public Expect required()
    {
        mapLastDescriptions((i, s) -> String.format("<%s>", stripOptionality(s)));
        return this;
    }

    public Expect optional()
    {
        mapLastDescriptions((i, s) -> String.format("[%s]", stripOptionality(s)));
        return this;
    }

    public Expect naked()
    {
        mapLastDescriptions((i, s) -> stripOptionality(s));
        return this;
    }

    public String usage()
    {
        return TextFormatting.RESET + Stream.concat(
                params.get(null).usage(),
                params.entrySet().stream()
                        .filter(e -> e.getKey() != null)
                        .filter(e -> e.getKey().equals(e.getValue().name))
                        .flatMap(e -> flags.contains(e.getKey()) ? Stream.of(keyRepresentation(e.getKey())) : e.getValue().usage()
                                .map(desc -> String.format("%s %s", keyRepresentation(e.getKey()), desc)))
        ).reduce("", NaP::join);
    }

    protected String keyRepresentation(String key)
    {
        List<String> aliases = Lists.newArrayList(this.aliases.get(key));

        for (Iterator<String> iterator = aliases.iterator(); iterator.hasNext(); )
        {
            String alias = iterator.next();
            if (key.contains(alias))
            {
                key = key.replaceFirst(alias, String.format("%s%s%s", TextFormatting.AQUA, alias, TextFormatting.RESET));
                iterator.remove();
            }
        }

        aliases.add(0, key);

        return Parameters.prefix(shortParams.contains(key)) + String.join("|", aliases);
    }

    public interface Completer
    {
        Object complete(MinecraftServer server, ICommandSender sender, Parameters parameters, @Nullable BlockPos pos);
    }

    protected class SuggestParameter
    {
        protected String name;
        protected final List<Completer> completions = new ArrayList<>();
        protected final List<String> descriptions = new ArrayList<>();
        protected boolean repeat;

        public SuggestParameter(String name)
        {
            this.name = name;
        }

        public SuggestParameter next(Completer completion)
        {
            completions.add(completion);
            descriptions.add(String.format("[%d]", completions.size()));
            return this;
        }

        public SuggestParameter or(Completer completion)
        {
            Completer prev = completions.remove(completions.size() - 1);
            completions.add((server, sender, parameters, pos) ->
                    Pair.of(prev.complete(server, sender, parameters, pos), completion.complete(server, sender, parameters, pos)));
            return this;
        }

        public Stream<String> usage()
        {
            return IntStream.range(0, descriptions.size())
                    .mapToObj(i -> String.format("%s%s%s%s",
                            TextFormatting.YELLOW, descriptions.get(i), TextFormatting.RESET,
                            repeat && i == descriptions.size() - 1 ? "..." : ""
                    ));
        }
    }
}
