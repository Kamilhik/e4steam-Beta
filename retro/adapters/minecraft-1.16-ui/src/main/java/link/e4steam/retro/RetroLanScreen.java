package link.e4steam.retro;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;

/** Mapped widgets only; preferences and server mutations are shared across retro versions. */
public final class RetroLanScreen extends Screen {
    private final Screen parent;
    private RetroLanSettings editor;
    private EditBox players, port;
    private Button done;
    public RetroLanScreen(Screen parent) {
        super(new TranslatableComponent("text.e4steam_minecraft.lan.settings"));this.parent=parent;
    }
    @Override protected void init() {
        if(minecraft.getSingleplayerServer()==null){minecraft.setScreen(parent);return;}
        if(editor==null)editor=new RetroLanSettings(minecraft.getSingleplayerServer());
        String playerText=players==null?Integer.toString(editor.draft.maxPlayers()):players.getValue();
        String portText=port==null?Integer.toString(editor.draft.port()):port.getValue();
        int x=width/2-155,y=Math.max(6,height/2-90);
        addButton(new Button(x,y+26,150,20,new TextComponent(I18n.get("text.e4steam_minecraft.lan.gameMode",I18n.get("gameMode."+editor.draft.gameMode()))),button -> {
            editor.cycleMode();button.setMessage(new TextComponent(I18n.get("text.e4steam_minecraft.lan.gameMode",I18n.get("gameMode."+editor.draft.gameMode()))));
        }));
        addButton(new Button(x+160,y+26,150,20,new TextComponent(I18n.get("text.e4steam_minecraft.lan.cheats",I18n.get(editor.draft.allowCheats()?"options.on":"options.off"))),button -> {
            editor.draft=editor.draft.withAllowCheats(!editor.draft.allowCheats());button.setMessage(new TextComponent(I18n.get("text.e4steam_minecraft.lan.cheats",I18n.get(editor.draft.allowCheats()?"options.on":"options.off"))));
        }));
        addButton(new Button(x,y+50,310,20,new TextComponent((I18n.get("text.e4steam_minecraft.accessMode")+": "+I18n.get(RetroBootstrap.selectedAccessMode().translationKey()))),button -> {
            RetroBootstrap.cycleAccessMode();button.setMessage(new TextComponent((I18n.get("text.e4steam_minecraft.accessMode")+": "+I18n.get(RetroBootstrap.selectedAccessMode().translationKey()))));
        }));
        addButton(new Button(x,y+74,150,20,new TextComponent(I18n.get("text.e4steam_minecraft.lan.onlineMode",I18n.get(editor.draft.onlineMode()?"options.on":"options.off"))),button -> {
            editor.draft=editor.draft.withOnlineMode(!editor.draft.onlineMode());button.setMessage(new TextComponent(I18n.get("text.e4steam_minecraft.lan.onlineMode",I18n.get(editor.draft.onlineMode()?"options.on":"options.off"))));
        }));
        addButton(new Button(x+160,y+74,150,20,new TextComponent(I18n.get("text.e4steam_minecraft.lan.pvp",I18n.get(editor.draft.pvp()?"options.on":"options.off"))),button -> {
            editor.draft=editor.draft.withPvp(!editor.draft.pvp());button.setMessage(new TextComponent(I18n.get("text.e4steam_minecraft.lan.pvp",I18n.get(editor.draft.pvp()?"options.on":"options.off"))));
        }));
        players=new EditBox(font,x+94,y+100,56,20,new TextComponent(I18n.get("text.e4steam_minecraft.lan.players")));
        port=new EditBox(font,x+238,y+100,72,20,new TextComponent(I18n.get("text.e4steam_minecraft.lan.port")));
        players.setMaxLength(2);port.setMaxLength(5);
        players.setFilter(value -> value.matches("[0-9]*"));port.setFilter(value -> value.matches("[0-9]*"));
        players.setValue(playerText);port.setValue(portText);
        port.setEditable(!editor.published);children.add(players);children.add(port);
        done=addButton(new Button(x,y+140,150,20,new TextComponent(I18n.get(editor.published?"text.e4steam_minecraft.lan.apply":"lanServer.start")),button -> {
            if(editor.validate(players.getValue(),port.getValue())) {
                button.active=false;
                editor.save().thenAccept(success -> RetroBootstrap.executeClient(() -> {
                    if(success)minecraft.setScreen(null);
                    else {button.active=true;button.setMessage(new TextComponent(I18n.get("text.e4steam_minecraft.lan.failed")));}
                }));
            }
        }));
        addButton(new Button(x+160,y+140,150,20,new TextComponent(I18n.get("gui.cancel")),button -> minecraft.setScreen(parent)));
        players.setResponder(value -> validate());port.setResponder(value -> validate());validate();
    }
    private void validate(){if(done!=null)done.active=editor.validate(players.getValue(),port.getValue());}
    @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, int x,int y,float delta) {
        int left=width/2-165, top=Math.max(6,height/2-90);
        fill(pose, 0,0,width,height,0xB80A1019);
        fill(pose, left,top-8,left+330,top+170,0xF216202C);
        fill(pose, left,top-8,left+330,top-6,0xFF54CFBC);
        fill(pose, left+6,top+22,left+324,top+97,0xFF202D3B);
        fill(pose, left+6,top+98,left+324,top+124,0xFF202D3B);
        drawCenteredString(pose, font,I18n.get("text.e4steam_minecraft.lan.settings"),width/2,top+5,0xF1F6FB);
        drawString(pose, font,I18n.get("text.e4steam_minecraft.lan.players"),left+10,top+106,0xB7C6D5);
        drawString(pose, font,I18n.get("text.e4steam_minecraft.lan.port"),left+170,top+106,0xB7C6D5);
        super.render(pose, x,y,delta);players.render(pose, x,y,delta);port.render(pose, x,y,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
}
