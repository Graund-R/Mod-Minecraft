package pocketmod;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ExileDebugGui extends GuiScreen {
    private static boolean thirstPage=ClientViewState.number("debug.thirst",0)==1;
    private static int radius = ExileConfig.debugAreaRadius;
    private static int selected=Math.max(0,Math.min(4,ClientViewState.number("debug.selected",1))),scroll=ClientViewState.number("debug.scroll",0);
    private int left() { return Math.max(8,width/2-210); }
    @Override public void initGui() {
        buttonList.clear();
        int left=left(),cw=Math.min(132,(width-22)/3);
        buttonList.add(new ShopStyle.Button(1,left,22,cw,20,Tr.format("pocketmod.simple.tab_world")));
        buttonList.add(new ShopStyle.Button(2,left+cw+3,22,cw,20,Tr.format("pocketmod.simple.tab_thirst")));
        buttonList.add(new ShopStyle.Button(3,left+2*(cw+3),22,cw,20,Tr.format("pocketmod.debug.settings")));
        if(!thirstPage) {
            int y=height-123,bw=Math.min(62,(width-22)/5);
            for(int i=0;i<5;i++)buttonList.add(new ShopStyle.Button(10+i,left+i*(bw+3),y,bw,20,Tr.format("pocketmod.debug.stage",i)));
            cw=Math.min(110,(width-22)/3);
            buttonList.add(new ShopStyle.Button(20,left,y+23,cw,20,Tr.format("pocketmod.debug.area",(2*radius+1)+"x"+(2*radius+1))));
            buttonList.add(new ShopStyle.Button(21,left+cw+3,y+23,cw,20,Tr.format("pocketmod.debug.pause")));
            buttonList.add(new ShopStyle.Button(22,left+2*(cw+3),y+23,cw,20,Tr.format("pocketmod.debug.wave")));
            buttonList.add(new ShopStyle.Button(23,left,y+46,cw,20,Tr.format("pocketmod.debug.clear")));
            buttonList.add(new ShopStyle.Button(24,left+cw+3,y+46,cw,20,Tr.format("pocketmod.debug.borders")));
            buttonList.add(new ShopStyle.Button(25,left+2*(cw+3),y+92,cw,20,Tr.format("gui.done")));
            buttonList.add(new ShopStyle.Button(26,left,y+69,cw,20,Tr.format("pocketmod.debug.age_one")));
            buttonList.add(new ShopStyle.Button(27,left+cw+3,y+69,cw,20,Tr.format("pocketmod.debug.age_five")));
            buttonList.add(new ShopStyle.Button(28,left+2*(cw+3),y+46,cw,20,Tr.format("pocketmod.debug.auto_toggle")));
            buttonList.add(new ShopStyle.Button(29,left+2*(cw+3),y+69,cw,20,Tr.format("pocketmod.debug.cancel_pending")));
            buttonList.add(new ShopStyle.Button(42,left,y+92,cw,20,Tr.format("pocketmod.debug.exposure_minus")));
            buttonList.add(new ShopStyle.Button(43,left+cw+3,y+92,cw,20,Tr.format("pocketmod.debug.exposure_plus")));
        } else {
            cw=Math.min(132,(width-22)/3);
            String[] labels={"minus","plus","full","accel_minus","accel_plus","calm","pause","speed","night","model","kill","reset"};
            for(int i=0;i<labels.length;i++)buttonList.add(new ShopStyle.Button(30+i,left+(i%3)*(cw+3),height-123+(i/3)*23,cw,20,Tr.format("pocketmod.simple.button_"+labels[i])));
            buttonList.add(new ShopStyle.Button(25,left,height-31,3*cw+6,20,Tr.format("gui.done")));
        }
    }
    private void send(int op,int value) { NetworkHandler.action(NetAction.EXILE_DEBUG,op,value,radius,""); }
    @Override protected void actionPerformed(GuiButton b)throws IOException {
        if(b.id==1||b.id==2){thirstPage=b.id==2;scroll=0;initGui();return;}
        if(b.id==3){mc.displayGuiScreen(new ExileSettingsGui(this));return;}
        if(b.id>=10&&b.id<=14){selected=b.id-10;send(1,selected);}
        if(b.id==20){radius=(radius+1)%5;initGui();}
        if(b.id==21)send(4,0);
        if(b.id==22&&selected>0)send(2,selected);
        if(b.id==23)send(3,0);
        if(b.id==24)ExileDebugClient.borders=!(ExileDebugClient.borders == null ? ExileConfig.debugBorders : ExileDebugClient.borders);
        if(b.id==25)mc.displayGuiScreen(null);
        if(b.id==26)send(5,1);
        if(b.id==27)send(5,5);
        if(b.id==28)send(6,0);
        if(b.id==29)send(7,0);
        if(b.id==42)send(8,-30);
        if(b.id==43)send(8,30);
        if(b.id==30)send(10,-1);
        if(b.id==31)send(10,1);
        if(b.id==32)send(11,1);
        if(b.id==33)send(12,-1);
        if(b.id==34)send(12,1);
        if(b.id==35)send(18,0);
        if(b.id==36)send(14,0);
        if(b.id==37)send(13,0);
        if(b.id==38)send(17,0);
        if(b.id==39)send(15,0);
        if(b.id==40)send(16,0);
        if(b.id==41)send(19,0);
    }
    private String[] thirstLines(NBTTagCompound s) {
        if(s.getBoolean("thirst_cyclic_active"))return new String[]{
            Tr.format("pocketmod.simple.model",Tr.format("pocketmod.simple.model_cyclic")),
            Tr.format("pocketmod.cycle.debug_value",String.format(java.util.Locale.ROOT,"%.1f",s.getDouble("cycle_thirst"))),
            Tr.format("pocketmod.simple.status",Tr.format("pocketmod.simple.status_"+s.getString("simple_status"))),
            Tr.format("pocketmod.cycle.debug_habit",String.format(java.util.Locale.ROOT,"%.2f",s.getDouble("cycle_habit"))),
            Tr.format("pocketmod.cycle.debug_rate",String.format(java.util.Locale.ROOT,"%.2f",s.getDouble("cycle_rate")),s.getInteger("simple_speed")),
            Tr.format("pocketmod.cycle.debug_relief",String.format(java.util.Locale.ROOT,"%.1f",s.getDouble("cycle_ordinary_relief"))),
            Tr.format("pocketmod.cycle.debug_calm",s.getInteger("cycle_calm_remaining"),s.getInteger("cycle_boss_count")),
            Tr.format("pocketmod.cycle.debug_rule")};
        boolean simple=s.getBoolean("thirst_simple_active");
        java.util.List<String> lines=new java.util.ArrayList<>();
        lines.add(Tr.format("pocketmod.simple.model",Tr.format("pocketmod.simple.model_"+(simple?"simple":"legacy"))));
        lines.add(simple?Tr.format("pocketmod.simple.bar",s.getInteger("simple_points"),s.getInteger("simple_limit")):
                Tr.format("pocketmod.simple.legacy_value",s.getInteger("thirst"),s.getInteger("config_thirst")));
        lines.add(Tr.format("pocketmod.simple.status",Tr.format("pocketmod.simple.status_"+s.getString("simple_status"))));
        if(simple) {
            lines.add(Tr.format("pocketmod.simple.rate",s.getInteger("simple_accel"),s.getInteger("simple_accel_max"),String.format(java.util.Locale.ROOT,"%.2f",s.getDouble("simple_rate")),s.getInteger("simple_speed")));
            lines.add(Tr.format("pocketmod.simple.eta",s.getInteger("simple_eta"),s.getInteger("simple_accel_eta")));
            int time=s.getInteger("debug_thirst_night");
            lines.add(Tr.format("pocketmod.simple.night",Tr.format("pocketmod.simple.time_"+time),Tr.format("pocketmod.simple.time_"+(s.getBoolean("simple_night")?2:1))));
            lines.add(Tr.format("pocketmod.simple.kill_rule"));
        }
        lines.add(Tr.format("pocketmod.simple.test_hint"));
        return lines.toArray(new String[0]);
    }
    @Override public void drawScreen(int mx,int my,float partial) {
        drawDefaultBackground();drawCenteredString(fontRenderer,Tr.format("pocketmod.debug.title"),width/2,8,0xFFFFFF);
        if(mc.player==null)return;
        NBTTagCompound s=ExileMode.state(mc.player),d=s.getCompoundTag("debug_snapshot");
        int left=left(),y=48;
        java.util.List<String> wrapped=new java.util.ArrayList<>();
        for(String line:thirstPage?thirstLines(s):ExileDebugClient.lines(s,d))wrapped.addAll(fontRenderer.listFormattedStringToWidth(line,width-left-8));
        int available=Math.max(1,(height-y-131)/11);
        scroll=Math.min(scroll,Math.max(0,wrapped.size()-available));
        for(int i=0;i<Math.min(wrapped.size()-scroll,available);i++) {
            fontRenderer.drawStringWithShadow(wrapped.get(i+scroll),left,y,0xEEEEEE);y+=11;
        }
        for(GuiButton b:buttonList) {
            if((b.id>=10&&b.id<=14)||b.id==21||b.id==22||b.id==23||b.id==26||b.id==27||b.id==28||b.id==29||b.id==42||b.id==43)
                b.enabled=d.getBoolean("edit")&&mc.player.dimension==0&&(b.id!=22||selected>0);
            if(b.id>=30&&b.id<=41) {
                boolean simpleOnly=(b.id>=33&&b.id<=35)||b.id==37||b.id==38||b.id==40;
                boolean cyclic=s.getBoolean("thirst_cyclic_active");
                b.enabled=d.getBoolean("edit")&&(!simpleOnly||s.getBoolean("thirst_simple_active")||(cyclic&&b.id!=38));
            }
            if(b.id>=10&&b.id<=14)b.displayString=(selected==b.id-10?"> ":"")+Tr.format("pocketmod.debug.stage",b.id-10);
        }
        super.drawScreen(mx,my,partial);
        for (GuiButton b : buttonList) if (b.isMouseOver()) {
            drawHoveringText(b.displayString, mx, my);
            break;
        }
    }
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();
        if(wheel!=0)scroll=Math.max(0,scroll+(wheel>0?-1:1));
    }
    @Override public void onGuiClosed(){ClientViewState.save("debug.","thirst",thirstPage?1:0,"selected",selected,"scroll",scroll);}
    @Override public boolean doesGuiPauseGame(){return true;}
}
