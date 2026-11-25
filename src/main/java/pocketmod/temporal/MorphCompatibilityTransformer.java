package pocketmod.temporal;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Optional compatibility for legacy Metamorph. Preserve capability logic, only make world-side reads null-safe. */
public final class MorphCompatibilityTransformer implements IClassTransformer {
    private static final String TARGET="mchorse.metamorph.capabilities.CapabilityHandler";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null || !TARGET.equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if(!"attachCapability".equals(method.name))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray()){
                if(!(instruction instanceof FieldInsnNode))continue;
                FieldInsnNode field=(FieldInsnNode)instruction;
                if(field.getOpcode()==Opcodes.GETFIELD && "net/minecraft/world/World".equals(field.owner)
                        && ("isRemote".equals(field.name)||"field_72995_K".equals(field.name)) && "Z".equals(field.desc)){
                    method.instructions.set(field,new MethodInsnNode(Opcodes.INVOKESTATIC,"pocketmod/MorphWorldCompat","isRemote","(Ljava/lang/Object;)Z",false));changed=true;
                }
            }
        }
        if(!changed)return bytes;
        // GETFIELD World -> boolean and the helper have the same stack effect. Original frames remain valid.
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
}
