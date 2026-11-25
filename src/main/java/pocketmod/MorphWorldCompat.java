package pocketmod;

import net.minecraft.world.World;

/** RocketSquids can deserialize an item-contained entity without assigning it a world yet. */
public final class MorphWorldCompat {
    private MorphWorldCompat(){}
    public static boolean isRemote(Object world){return world instanceof World && ((World)world).isRemote;}
}
