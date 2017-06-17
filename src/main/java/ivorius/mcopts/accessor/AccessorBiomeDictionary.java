/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.mcopts.accessor;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import ivorius.mcopts.reflection.SafeReflector;
import net.minecraftforge.common.BiomeDictionary;

import java.util.*;

/**
 * Created by lukas on 08.06.17.
 */
public class AccessorBiomeDictionary
{
    public static String serializedName(Enum anEnum)
    {
        String name = anEnum.name();

        try
        {
            SerializedName serializedName = anEnum.getClass().getField(name).getAnnotation(SerializedName.class);

            if (serializedName != null)
            {
                name = serializedName.value();
            }
        }
        catch (NoSuchFieldException ignored)
        {

        }

        return name;
    }

    public static BiomeDictionary.Type getTypeWeak(String var)
    {
        return getMap().get(var.toUpperCase());
    }

    public static Map<String, BiomeDictionary.Type> getMap()
    {
        HashMap<String, BiomeDictionary.Type> map = new HashMap<>();

        for (BiomeDictionary.Type type : BiomeDictionary.Type.values())
            map.put(serializedName(type), type);

        return map;
    }

    public static void addSubtypes(BiomeDictionary.Type type, BiomeDictionary.Type... subtypes)
    {
        addSubtypes(type, Arrays.asList(subtypes));
    }

    public static void addSubtypes(BiomeDictionary.Type type, List<BiomeDictionary.Type> subtypes)
    {
        setSubtypes(type, Lists.newArrayList(Iterables.concat(getSubtypes(type), subtypes)));
    }

    public static List<BiomeDictionary.Type> getSubtypes(BiomeDictionary.Type type)
    {
        return SafeReflector.get(BiomeDictionary.Type.class, "subTags", type, new ArrayList<>());
    }

    public static void setSubtypes(BiomeDictionary.Type type, List<BiomeDictionary.Type> types)
    {
        SafeReflector.of(BiomeDictionary.Type.class, "subTags", field -> field.set(type, types));
    }
}
