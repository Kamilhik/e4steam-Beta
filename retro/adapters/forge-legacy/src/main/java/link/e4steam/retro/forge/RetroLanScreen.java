package link.e4steam.retro.forge;

import link.e4steam.retro.RetroBootstrap;
import link.e4steam.retro.RetroLanSettings;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

/** Version-specific widgets backed by the shared e4steam LAN editor. */
public final class RetroLanScreen extends GuiScreen {
    private final GuiScreen parent;
    private RetroLanSettings editor;
    private GuiTextField players, port;
    private GuiButton done;
    public RetroLanScreen(GuiScreen parent) { this.parent = parent; }
    @Override public void initGui() {
        if (mc.getIntegratedServer() == null) { mc.displayGuiScreen(parent); return; }
        if (editor == null) editor = new RetroLanSettings(mc.getIntegratedServer());
        String playerText=players==null?Integer.toString(editor.draft.maxPlayers()):players.getText();
        String portText=port==null?Integer.toString(editor.draft.port()):port.getText();
        buttonList.clear();
        int x = width / 2 - 155, y = Math.max(24, height / 2 - 70);
        buttonList.add(new GuiButton(1,x,y,150,20,mode()));
        buttonList.add(new GuiButton(2,x+160,y,150,20,state("cheats",editor.draft.allowCheats())));
        buttonList.add(new GuiButton(3,x,y+24,310,20,access()));
        buttonList.add(new GuiButton(4,x,y+48,150,20,state("onlineMode",editor.draft.onlineMode())));
        buttonList.add(new GuiButton(5,x+160,y+48,150,20,state("pvp",editor.draft.pvp())));
        players = new GuiTextField(fontRendererObj,x+94,y+80,56,20);
        port = new GuiTextField(fontRendererObj,x+238,y+80,72,20);
        players.setMaxStringLength(2); port.setMaxStringLength(5);
        players.setText(playerText);
        port.setText(portText); port.setEnabled(!editor.published);
        done = new GuiButton(6,x,y+112,150,20,I18n.format(editor.published ? "text.e4steam_minecraft.lan.apply" : "lanServer.start"));
        buttonList.add(done);
        buttonList.add(new GuiButton(7,x+160,y+112,150,20,I18n.format("gui.cancel")));
        done.enabled=editor.validate(players.getText(),port.getText());
    }
    private String mode() { return I18n.format("gameMode."+editor.draft.gameMode()); }
    private String state(String key, boolean value) { return I18n.format("text.e4steam_minecraft.lan."+key,I18n.format(value?"options.on":"options.off")); }
    private String access() { return I18n.format("text.e4steam_minecraft.accessMode")+": "+I18n.format(RetroBootstrap.selectedAccessMode().translationKey()); }
    @Override protected void actionPerformed(GuiButton button) {
        if (editor.saving) return;
        switch(button.id) {
            case 1: editor.cycleMode();button.displayString=mode();break;
            case 2: editor.draft=editor.draft.withAllowCheats(!editor.draft.allowCheats());button.displayString=state("cheats",editor.draft.allowCheats());break;
            case 3: RetroBootstrap.cycleAccessMode();button.displayString=access();break;
            case 4: editor.draft=editor.draft.withOnlineMode(!editor.draft.onlineMode());button.displayString=state("onlineMode",editor.draft.onlineMode());break;
            case 5: editor.draft=editor.draft.withPvp(!editor.draft.pvp());button.displayString=state("pvp",editor.draft.pvp());break;
            case 6:
                if(editor.validate(players.getText(),port.getText())) editor.save().thenAccept(success -> {
                    if(success) RetroBootstrap.executeClient(() -> mc.displayGuiScreen(null));
                });
                break;
            case 7: mc.displayGuiScreen(parent);break;
            default: break;
        }
    }
    @Override protected void keyTyped(char c,int key)  {
        if(editor.saving)return;
        if(key==1){mc.displayGuiScreen(parent);return;}
        players.textboxKeyTyped(c,key);port.textboxKeyTyped(c,key);
        done.enabled=editor.validate(players.getText(),port.getText());
    }
    @Override protected void mouseClicked(int x,int y,int button)  {
        super.mouseClicked(x,y,button);players.mouseClicked(x,y,button);port.mouseClicked(x,y,button);
    }
    @Override public void drawScreen(int x,int y,float delta) {
        int left=width/2-165, panelTop=Math.max(4,height/2-108);
        drawRect(0,0,width,height,0xB80A1019);
        drawRect(left,panelTop,left+330,panelTop+198,0xF216202C);
        drawRect(left,panelTop,left+330,panelTop+2,0xFF54CFBC);
        int row=Math.max(24,height/2-70);
        drawRect(left+6,row-4,left+324,row+73,0xFF202D3B);
        drawRect(left+6,row+76,left+324,row+105,0xFF202D3B);
        drawCenteredString(fontRendererObj,I18n.format("text.e4steam_minecraft.lan.settings"),width/2,Math.max(8,height/2-98),0xF1F6FB);
        int top=Math.max(24,height/2-70);
        drawString(fontRendererObj,I18n.format("text.e4steam_minecraft.lan.players"),width/2-155,top+86,0xffffff);
        drawString(fontRendererObj,I18n.format("text.e4steam_minecraft.lan.port"),width/2+5,top+86,0xffffff);
        super.drawScreen(x,y,delta);players.drawTextBox();port.drawTextBox();
        if(!editor.error.isEmpty())drawCenteredString(fontRendererObj,editor.error,width/2,top+138,0xff5555);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
