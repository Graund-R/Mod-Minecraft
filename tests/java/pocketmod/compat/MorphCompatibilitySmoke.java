package pocketmod;

import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import pocketmod.temporal.MorphCompatibilityTransformer;

/** Execute the legacy null-world failure and repaired paths under JVM verification, without the game. */
public final class MorphCompatibilitySmoke implements Opcodes {
    private static final String WORLD="net/minecraft/world/World",TARGET="mchorse/metamorph/capabilities/CapabilityHandler",HOOK="pocketmod/MorphWorldCompat";
    private static ClassWriter type(String name){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);w.visit(V1_8,ACC_PUBLIC,name,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();return w;
    }
    private static byte[] world(String field){ClassWriter w=type(WORLD);w.visitField(ACC_PUBLIC,field,"Z",null,null).visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] handler(String field){
        ClassWriter w=type(TARGET);w.visitField(ACC_PUBLIC,"capabilities","I",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(ACC_PUBLIC,"attachCapability","(L"+WORLD+";Z)V",null,null);m.visitCode();
        m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,1);m.visitFieldInsn(GETFIELD,WORLD,field,"Z");m.visitFieldInsn(PUTFIELD,TARGET,"capabilities","I");
        Label end=new Label();m.visitVarInsn(ILOAD,2);m.visitJumpInsn(IFEQ,end);
        m.visitVarInsn(ALOAD,0);m.visitInsn(DUP);m.visitFieldInsn(GETFIELD,TARGET,"capabilities","I");m.visitInsn(ICONST_2);m.visitInsn(IOR);m.visitFieldInsn(PUTFIELD,TARGET,"capabilities","I");
        m.visitLabel(end);m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] hook(String field){
        ClassWriter w=type(HOOK);MethodVisitor m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"isRemote","(Ljava/lang/Object;)Z",null,null);m.visitCode();
        Label nonnull=new Label();m.visitVarInsn(ALOAD,0);m.visitJumpInsn(IFNONNULL,nonnull);m.visitInsn(ICONST_0);m.visitInsn(IRETURN);
        m.visitLabel(nonnull);m.visitVarInsn(ALOAD,0);m.visitTypeInsn(CHECKCAST,WORLD);m.visitFieldInsn(GETFIELD,WORLD,field,"Z");m.visitInsn(IRETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static ClassLoader loader(String field,boolean patch){
        byte[] original=handler(field);MorphCompatibilityTransformer transformer=new MorphCompatibilityTransformer();
        if(transformer.transform("unrelated.Mod","unrelated.Mod",original)!=original)throw new AssertionError("unrelated mod was changed");
        byte[] code=patch?transformer.transform(TARGET.replace('/','.'),TARGET.replace('/','.'),original):original;
        if(patch&&transformer.transform(TARGET.replace('/','.'),TARGET.replace('/','.'),code)!=code)throw new AssertionError("patch is not idempotent");
        final Map<String,byte[]> definitions=new HashMap<String,byte[]>();definitions.put(WORLD.replace('/','.'),world(field));definitions.put(TARGET.replace('/','.'),code);definitions.put(HOOK.replace('/','.'),hook(field));
        return new ClassLoader(MorphCompatibilitySmoke.class.getClassLoader()){
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!definitions.containsKey(name))return super.loadClass(name,resolve);Class<?> c=findLoadedClass(name);
                if(c==null){byte[] b=definitions.get(name);c=defineClass(name,b,0,b.length);}if(resolve)resolveClass(c);return c;
            }
        };
    }
    private static void check(String field,boolean patch)throws Exception{
        ClassLoader loader=loader(field,patch);Class<?> world=loader.loadClass(WORLD.replace('/','.')),handler=loader.loadClass(TARGET.replace('/','.'));
        Object instance=handler.newInstance();Method method=handler.getMethod("attachCapability",world,boolean.class);
        try{method.invoke(instance,null,false);if(!patch)throw new AssertionError("legacy crash did not reproduce");}
        catch(InvocationTargetException e){if(patch||!(e.getCause() instanceof NullPointerException))throw e;return;}
        if(handler.getField("capabilities").getInt(instance)!=0)throw new AssertionError("temporary entity got client model capability");
        method.invoke(instance,null,true);if(handler.getField("capabilities").getInt(instance)!=2)throw new AssertionError("player capability branch was suppressed");
        for(boolean remote:new boolean[]{false,true}){
            Object assigned=world.newInstance();world.getField(field).setBoolean(assigned,remote);
            method.invoke(instance,assigned,true);if(handler.getField("capabilities").getInt(instance)!=(remote?3:2))throw new AssertionError("normal client/server capability logic changed");
        }
    }
    public static void main(String[] args)throws Exception{
        if(MorphWorldCompat.isRemote(null)||MorphWorldCompat.isRemote(new Object()))throw new AssertionError("real null-world hook");
        for(String field:new String[]{"isRemote","field_72995_K"}){check(field,false);check(field,true);}
        System.out.println("Metamorph compatibility: legacy NPE reproduced; null-world, player and normal world paths pass in MCP/SRG; optional/idempotent transform");
    }
}
