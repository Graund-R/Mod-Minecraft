package pocketmod;

import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.*;

@SideOnly(Side.CLIENT)
public final class TemporalGui extends GuiScreen {
    private int lapse;
    private ItemStack item(){return mc.player==null?ItemStack.EMPTY:TemporalDialItem.held(mc.player);}
    @Override public void initGui(){
        ItemStack item=item();if(item.isEmpty()){mc.displayGuiScreen(null);return;}
        lapse=TemporalDialItem.data(item).getInteger("lapse");
        int x=width/2,y=height/2-62;
        buttonList.clear();
        buttonList.add(new GuiButton(0,x-102,y,48,20,"−5 s"));buttonList.add(new GuiButton(1,x+54,y,48,20,"+5 s"));
        buttonList.add(new GuiButton(2,x-102,y+26,204,20,""));
        buttonList.add(new GuiButton(3,x-102,y+52,204,20,tr("charge")));
        buttonList.add(new GuiButton(4,x-102,y+78,204,20,tr("rewind_charge")));
        buttonList.add(new GuiButton(5,x-102,y+104,204,20,tr("upgrade")));
        buttonList.add(new GuiButton(6,x-102,y+130,204,20,tr("close")));
    }
    private String tr(String key){return net.minecraft.client.resources.I18n.format("pocketmod.temporal."+key);}
    @Override protected void actionPerformed(GuiButton button)throws IOException {
        if(button.id==6){mc.displayGuiScreen(null);return;}
        ItemStack item=item();if(item.isEmpty())return;
        if(button.id<=1){lapse=Math.max(1,Math.min(TemporalDialItem.tier(item)==2?60:(TemporalDialItem.tier(item)+1)*10,lapse+(button.id==0?-5:5)));TemporalClient.action(5,lapse);}
        if(button.id==2)TemporalClient.action(6,0);
        if(button.id==3)TemporalClient.action(7,0);
        if(button.id==4)TemporalClient.action(8,0);
        if(button.id==5)TemporalClient.action(9,0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        drawDefaultBackground();ItemStack stack=item();if(stack.isEmpty()){mc.displayGuiScreen(null);return;}
        int x=width/2,y=height/2-62;NBTTagCompound n=TemporalDialItem.data(stack);
        drawRect(x-118,y-55,x+118,y+160,0xEE101A29);
        drawCenteredString(fontRenderer,stack.getDisplayName(),x,y-42,0xBDEAFF);
        drawCenteredString(fontRenderer,net.minecraft.client.resources.I18n.format("pocketmod.temporal.tooltip",n.getInteger("charges"),n.getInteger("rewind")/20),x,y-24,0xEEEEEE);
        drawCenteredString(fontRenderer,lapse+" s",x,y+6,0xFFFFFF);
        buttonList.get(2).displayString=tr(n.getBoolean("share")?"share_on":"share_off");
        boolean busy=PlayerData.root(mc.player).getCompoundTag("temporal").getInteger("mode")!=0;
        for(GuiButton b:buttonList)b.enabled=b.id==6||!busy;
        buttonList.get(4).enabled=!busy&&TemporalDialItem.tier(stack)>0;
        buttonList.get(5).enabled=!busy&&TemporalDialItem.tier(stack)<2;
        super.drawScreen(mouseX,mouseY,partialTicks);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
