package pocketmod;

import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraftforge.fml.relauncher.*;

/** A single ability overview; the shop owns branch unlocks and upgrades. */
@SideOnly(Side.CLIENT)
public final class TemporalGui extends GuiScreen {
    private String tr(String key,Object... args){return net.minecraft.client.resources.I18n.format("pocketmod.temporal."+key,args);}
    @Override public void initGui(){
        buttonList.clear();int y=height/2-50;
        AbilityType[] branches={AbilityType.SLOW,AbilityType.TIME_REWIND,AbilityType.TIME_FUTURE};
        for(int i=0;i<branches.length;i++){
            AbilityType branch=branches[i];if(!ExileProgression.available(mc.player,branch.id))continue;
            GuiButton button=new GuiButton(i,width/2-140,y,280,20,Tr.format(branch.titleKey())+" · "+AbilityStorage.getLevel(mc.player,branch)+"/"+branch.maxLevel);
            button.enabled=TimeBranches.unlocked(mc.player,branch);buttonList.add(button);y+=25;
        }
        buttonList.add(new GuiButton(3,width/2-140,y+5,138,20,tr("shop")));
        buttonList.add(new GuiButton(4,width/2+2,y+5,138,20,Tr.format("gui.done")));
    }
    @Override protected void actionPerformed(GuiButton button)throws IOException{
        if(button.id==4){mc.displayGuiScreen(null);return;}
        if(button.id==3){mc.displayGuiScreen(new ShopGui(mc.player));return;}
        mc.displayGuiScreen(null);
        TemporalClient.action(button.id==0?10:button.id==1?3:1,0);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        drawDefaultBackground();drawCenteredString(fontRenderer,tr("family"),width/2,height/2-100,0xBDEAFF);
        drawCenteredString(fontRenderer,tr("keys",ClientHandler.keyName("slow"),TemporalClient.rewindKey(),TemporalClient.futureKey()),width/2,height/2-80,0xEEEEEE);
        super.drawScreen(mouseX,mouseY,partial);
    }
    @Override public boolean doesGuiPauseGame(){return true;}
}
