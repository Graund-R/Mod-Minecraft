package pocketmod.temporal;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Inspects real Forge bytecode in MCP and SRG namespaces without booting Minecraft. */
public final class TemporalTransformerSmoke {
    private static final String[] CLASSES={"net.minecraft.world.World","net.minecraft.world.WorldServer",
        "net.minecraft.client.particle.ParticleManager","net.minecraft.client.renderer.texture.TextureMap",
        "net.minecraft.entity.player.EntityPlayerMP","net.minecraft.network.NetHandlerPlayServer",
        "net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher"};
    private static final Map<String,String> SRG=new HashMap<String,String>();
    static {
        String[][] pairs={{"updateEntityWithOptionalForce","func_72866_a"},{"updateWeather","func_72979_l"},{"updateBlocks","func_147456_g"},
          {"sendQueuedBlockEvents","func_147488_Z"},{"tickUpdates","func_72955_a"},{"setBlockState","func_180501_a"},{"updateEntities","func_72939_s"},{"update","func_73660_a"},
          {"render","func_192854_a"},{"onUpdate","func_70071_h_"},{"updateEffects","func_78868_a"},{"updateAnimations","func_94248_c"},{"onUpdateEntity","func_71127_g"},
          {"processPlayer","func_147347_a"},{"processPlayerDigging","func_147345_a"},{"processTryUseItem","func_147346_a"},
          {"processTryUseItemOnBlock","func_184337_a"},{"processUseEntity","func_147340_a"},{"processClickWindow","func_147351_a"},
          {"processCreativeInventoryAction","func_147344_a"},{"processHeldItemChange","func_147355_a"},{"processInput","func_147358_a"}};
        for(String[] pair:pairs)SRG.put(pair[0],pair[1]);
    }
    public static void main(String[] args)throws Exception{
        try(JarFile jar=new JarFile(args[0])) {
            for(String name:CLASSES){
                byte[] original=read(jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class")));
                validate(name,original);
                ClassNode srg=new ClassNode();new ClassReader(original).accept(srg,0);
                for(MethodNode method:srg.methods) {
                    method.name=SRG.getOrDefault(method.name,method.name);
                    for(AbstractInsnNode n:method.instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)n;m.name=SRG.getOrDefault(m.name,m.name);}
                }
                ClassWriter out=new ClassWriter(0);srg.accept(out);validate(name,out.toByteArray());
            }
        }
        executeGates();
        System.out.println("Temporal hooks: MCP/SRG bytecode and JVM execution passed");
    }
    private static byte[] read(InputStream in)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int count;while((count=in.read(b))>=0)out.write(b,0,count);return out.toByteArray();}
    private static void validate(String name,byte[] data)throws Exception {
        byte[] transformed=new TemporalTransformer().transform(name,name,data);
        ClassNode c=new ClassNode();new ClassReader(transformed).accept(c,ClassReader.EXPAND_FRAMES);
        int hooks=0;
        for(MethodNode method:c.methods){
            boolean touched=false;
            for(AbstractInsnNode n:method.instructions.toArray())if(n instanceof MethodInsnNode && ((MethodInsnNode)n).owner.equals("pocketmod/TemporalHooks")){hooks++;touched=true;}
            if(touched)new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,method);
        }
        int expected=name.endsWith("TileEntityRendererDispatcher")?2:name.endsWith("World")?5:name.endsWith("WorldServer")?3:name.endsWith("NetHandlerPlayServer")?9:1;
        if(hooks!=expected)throw new AssertionError(name+" hooks: "+hooks);
    }
    // Load tiny transformed fixtures under JVM verification, including the new stack-map frames.
    private static void executeGates()throws Exception {
        ClassWriter hooks=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        hooks.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,"pocketmod/TemporalHooks",null,"java/lang/Object",null);
        hooks.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"frozen","Z",null,null).visitEnd();
        for(String name:new String[]{"skipEntity","frozenWorld","beforeBlock","frozenClient"}){
            String desc=name.equals("frozenClient")?"()Z":name.equals("beforeBlock")?"(Ljava/lang/Object;Ljava/lang/Object;)Z":"(Ljava/lang/Object;)Z";
            MethodVisitor m=hooks.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,name,desc,null,null);m.visitCode();m.visitFieldInsn(Opcodes.GETSTATIC,"pocketmod/TemporalHooks","frozen","Z");m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        MethodVisitor tile=hooks.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"tickTile","(Ljava/lang/Object;)V",null,null);tile.visitCode();tile.visitInsn(Opcodes.RETURN);tile.visitMaxs(0,0);tile.visitEnd();
        MethodVisitor weather=hooks.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"tickWeatherEntity","(Ljava/lang/Object;)V",null,null);weather.visitCode();weather.visitInsn(Opcodes.RETURN);weather.visitMaxs(0,0);weather.visitEnd();hooks.visitEnd();
        ClassWriter world=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        world.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,"net/minecraft/world/World",null,"java/lang/Object",null);
        MethodVisitor ctor=world.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        world.visitField(Opcodes.ACC_PUBLIC,"count","I",null,null).visitEnd();
        for(String name:new String[]{"updateEntityWithOptionalForce","updateWeather","setBlockState","updateEntities"}){
            String desc=name.equals("setBlockState")?"(Ljava/lang/Object;Ljava/lang/Object;I)Z":name.equals("updateEntityWithOptionalForce")?"(Ljava/lang/Object;Z)V":name.equals("updateEntities")?"(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/ITickable;)V":"()V";
            MethodVisitor m=world.visitMethod(Opcodes.ACC_PUBLIC,name,desc,null,null);m.visitCode();
            if(name.equals("updateEntities")){m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/Entity","onUpdate","()V",false);m.visitVarInsn(Opcodes.ALOAD,2);m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"net/minecraft/util/ITickable","update","()V",true);}
            m.visitVarInsn(Opcodes.ALOAD,0);m.visitInsn(Opcodes.DUP);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/world/World","count","I");m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IADD);m.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraft/world/World","count","I");
            if(name.equals("setBlockState")){m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IRETURN);}else m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        world.visitEnd();
        final Map<String,byte[]> definitions=new HashMap<String,byte[]>();definitions.put("pocketmod.TemporalHooks",hooks.toByteArray());
        definitions.put("net.minecraft.world.World",new TemporalTransformer().transform("net.minecraft.world.World","net.minecraft.world.World",world.toByteArray()));
        ClassLoader loader=new ClassLoader(TemporalTransformerSmoke.class.getClassLoader()){
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!definitions.containsKey(name))return super.loadClass(name,resolve);
                Class<?> c=findLoadedClass(name);if(c==null){byte[] bytes=definitions.get(name);c=defineClass(name,bytes,0,bytes.length);}if(resolve)resolveClass(c);return c;
            }
        };
        Class<?> w=loader.loadClass("net.minecraft.world.World"),h=loader.loadClass("pocketmod.TemporalHooks");Object instance=w.newInstance();
        w.getMethod("updateWeather").invoke(instance);
        if(w.getField("count").getInt(instance)!=1)throw new AssertionError("normal world ticks");
        h.getField("frozen").setBoolean(null,true);w.getMethod("updateWeather").invoke(instance);
        w.getMethod("updateEntityWithOptionalForce",Object.class,boolean.class).invoke(instance,new Object(),true);
        Object result=w.getMethod("setBlockState",Object.class,Object.class,int.class).invoke(instance,new Object(),new Object(),3);
        if(!Boolean.FALSE.equals(result)||w.getField("count").getInt(instance)!=1)throw new AssertionError("stasis gates");
        h.getField("frozen").setBoolean(null,false);w.getMethod("updateWeather").invoke(instance);
        if(w.getField("count").getInt(instance)!=2)throw new AssertionError("resume gate");
    }
}
