package pocketmod.temporal;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Inserts guards after Forge's deobfuscation transformer. Both MCP and SRG names are supported. */
public final class TemporalTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "pocketmod/TemporalHooks";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean world = "net.minecraft.world.World".equals(transformedName);
        boolean server = "net.minecraft.world.WorldServer".equals(transformedName);
        boolean particles = "net.minecraft.client.particle.ParticleManager".equals(transformedName);
        boolean textures = "net.minecraft.client.renderer.texture.TextureMap".equals(transformedName);
        boolean player="net.minecraft.entity.player.EntityPlayerMP".equals(transformedName);
        boolean connection="net.minecraft.network.NetHandlerPlayServer".equals(transformedName);
        boolean tiles="net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher".equals(transformedName);
        if (!world && !server && !particles && !textures && !player && !connection && !tiles) return bytes;
        ClassNode c = new ClassNode();
        new ClassReader(bytes).accept(c, 0);
        int changes = 0;
        for (MethodNode m : c.methods) {
            if(tiles && named(m,"render","func_192854_a") && m.desc.endsWith("DDDFIF)V")) {
                InsnList begin=new InsnList();begin.add(new VarInsnNode(ALOAD,1));
                begin.add(new MethodInsnNode(INVOKESTATIC,HOOK,"beginTile","(Ljava/lang/Object;)V",false));
                m.instructions.insert(begin);
                for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==RETURN) {
                    m.instructions.insertBefore(n,new MethodInsnNode(INVOKESTATIC,HOOK,"endTile","()V",false));
                }
                changes++;
            }
            if(player && named(m,"onUpdateEntity","func_71127_g")){guard(m,"skipEntity",0,false);changes++;}
            if(connection && (named(m,"processPlayer","func_147347_a") || named(m,"processPlayerDigging","func_147345_a")
                    || named(m,"processTryUseItem","func_147346_a") || named(m,"processTryUseItemOnBlock","func_184337_a")
                    || named(m,"processUseEntity","func_147340_a") || named(m,"processClickWindow","func_147351_a")
                    || named(m,"processCreativeInventoryAction","func_147344_a") || named(m,"processHeldItemChange","func_147355_a")
                    || named(m,"processInput","func_147358_a"))){if(named(m,"processClickWindow","func_147351_a")) {
                InsnList gate=new InsnList();LabelNode next=new LabelNode();gate.add(new VarInsnNode(ALOAD,0));gate.add(new VarInsnNode(ALOAD,1));
                gate.add(new MethodInsnNode(INVOKESTATIC,HOOK,"skipClick","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                gate.add(new JumpInsnNode(IFEQ,next));gate.add(new InsnNode(RETURN));gate.add(next);gate.add(new FrameNode(F_SAME,0,null,0,null));m.instructions.insert(gate);
            }else guard(m,"skipConnection",0,false);changes++;}
            if (world && named(m, "updateEntityWithOptionalForce", "func_72866_a")) {
                guard(m, "skipEntity", 1, false); changes++;
            }
            if ((world && named(m,"updateWeather","func_72979_l"))
                    || (server && (named(m,"updateBlocks","func_147456_g") || named(m,"sendQueuedBlockEvents","func_147488_Z")))) {
                guard(m, "frozenWorld", 0, false); changes++;
            }
            if (server && named(m,"tickUpdates","func_72955_a")) {
                guard(m,"frozenWorld",0,true); changes++;
            }
            if (world && named(m,"setBlockState","func_180501_a")
                    && Type.getArgumentTypes(m.desc).length == 3) {
                InsnList before = new InsnList();
                before.add(new VarInsnNode(ALOAD,0)); before.add(new VarInsnNode(ALOAD,1));
                before.add(new MethodInsnNode(INVOKESTATIC,HOOK,"beforeBlock","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                LabelNode next=new LabelNode();before.add(new JumpInsnNode(IFEQ,next));before.add(new InsnNode(ICONST_0));before.add(new InsnNode(IRETURN));before.add(next);before.add(new FrameNode(F_SAME,0,null,0,null));
                m.instructions.insert(before); changes++;
            }
            if (world && named(m,"updateEntities","func_72939_s")) {
                for (AbstractInsnNode n : m.instructions.toArray()) {
                    if (!(n instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode)n;
                    if("net/minecraft/entity/Entity".equals(call.owner) && ("onUpdate".equals(call.name)||"func_70071_h_".equals(call.name))){
                        m.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,"tickWeatherEntity","(Ljava/lang/Object;)V",false));changes++;
                    }
                    if ("net/minecraft/util/ITickable".equals(call.owner)
                            && ("update".equals(call.name) || "func_73660_a".equals(call.name))) {
                        m.instructions.set(call, new MethodInsnNode(INVOKESTATIC,HOOK,"tickTile","(Ljava/lang/Object;)V",false));
                        changes++;
                    }
                }
            }
            if ((particles && named(m,"updateEffects","func_78868_a"))
                    || (textures && named(m,"updateAnimations","func_94248_c"))) {
                InsnList gate = new InsnList(); LabelNode next = new LabelNode();
                gate.add(new MethodInsnNode(INVOKESTATIC,HOOK,"frozenClient","()Z",false));
                gate.add(new JumpInsnNode(IFEQ,next)); gate.add(new InsnNode(RETURN)); gate.add(next); gate.add(new FrameNode(F_SAME,0,null,0,null));
                m.instructions.insert(gate); changes++;
            }
        }
        int expected = world ? 5 : server ? 3 : connection ? 9 : 1;
        if (changes != expected) throw new IllegalStateException("PocketTemporal: expected " + expected + " hooks in " + transformedName + ", found " + changes);
        // New early exits target fresh labels; retain existing frames and add a SAME frame at each gate.
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(out); return out.toByteArray();
    }
    private static boolean named(MethodNode m,String a,String b) { return a.equals(m.name)||b.equals(m.name); }
    private static void guard(MethodNode m,String hook,int local,boolean boolReturn) {
        InsnList code = new InsnList(); LabelNode next = new LabelNode();
        code.add(new VarInsnNode(ALOAD,local));
        code.add(new MethodInsnNode(INVOKESTATIC,HOOK,hook,"(Ljava/lang/Object;)Z",false));
        code.add(new JumpInsnNode(IFEQ,next));
        if (boolReturn) code.add(new InsnNode(ICONST_0));
        code.add(new InsnNode(boolReturn ? IRETURN : RETURN));
        code.add(next); code.add(new FrameNode(F_SAME,0,null,0,null));
        m.instructions.insert(code);
    }
}
