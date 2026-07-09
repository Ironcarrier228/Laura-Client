package via;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import im.laura.utils.render.ColorUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.ITextComponent;

import java.util.HashMap;
import java.util.Map;

public class VersionSelectScreen
        extends
        TextFieldWidget {

    private static final Map<String, Integer> VERSION_TO_PROTOCOL = new HashMap<>();
    
    static {
        VERSION_TO_PROTOCOL.put("1.16.5", 754);
        VERSION_TO_PROTOCOL.put("1.16.4", 754);
        VERSION_TO_PROTOCOL.put("1.16.3", 754);
        VERSION_TO_PROTOCOL.put("1.16.2", 754);
        VERSION_TO_PROTOCOL.put("1.16.1", 753);
        VERSION_TO_PROTOCOL.put("1.16", 753);
        VERSION_TO_PROTOCOL.put("1.15.2", 578);
        VERSION_TO_PROTOCOL.put("1.15.1", 575);
        VERSION_TO_PROTOCOL.put("1.15", 575);
        VERSION_TO_PROTOCOL.put("1.14.4", 498);
        VERSION_TO_PROTOCOL.put("1.14.3", 490);
        VERSION_TO_PROTOCOL.put("1.14.2", 485);
        VERSION_TO_PROTOCOL.put("1.14.1", 480);
        VERSION_TO_PROTOCOL.put("1.14", 477);
        VERSION_TO_PROTOCOL.put("1.13.2", 404);
        VERSION_TO_PROTOCOL.put("1.13.1", 401);
        VERSION_TO_PROTOCOL.put("1.13", 393);
        VERSION_TO_PROTOCOL.put("1.12.2", 340);
        VERSION_TO_PROTOCOL.put("1.12.1", 338);
        VERSION_TO_PROTOCOL.put("1.12", 335);
        VERSION_TO_PROTOCOL.put("1.11.2", 316);
        VERSION_TO_PROTOCOL.put("1.11.1", 316);
        VERSION_TO_PROTOCOL.put("1.11", 315);
        VERSION_TO_PROTOCOL.put("1.10.2", 210);
        VERSION_TO_PROTOCOL.put("1.10.1", 210);
        VERSION_TO_PROTOCOL.put("1.10", 210);
        VERSION_TO_PROTOCOL.put("1.9.4", 110);
        VERSION_TO_PROTOCOL.put("1.9.2", 109);
        VERSION_TO_PROTOCOL.put("1.9.1", 108);
        VERSION_TO_PROTOCOL.put("1.9", 107);
        VERSION_TO_PROTOCOL.put("1.8.9", 47);
        VERSION_TO_PROTOCOL.put("1.8.8", 47);
        VERSION_TO_PROTOCOL.put("1.8", 47);
    }

    public VersionSelectScreen(FontRenderer p_i232260_1_, int p_i232260_2_, int p_i232260_3_, int p_i232260_4_, int p_i232260_5_, ITextComponent p_i232260_6_) {
        super(p_i232260_1_, p_i232260_2_, p_i232260_3_, p_i232260_4_, p_i232260_5_, p_i232260_6_);
        setText("1.16.5");
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        ProtocolVersion protocolVersion = getProtocolVersionByName(getText());
        if (protocolVersion == null) {
            setTextColor(ColorUtils.rgba(200,20,20,255));
        } else {
            ViaLoadingBase.getInstance().reload(protocolVersion);
            setTextColor(ColorUtils.rgba(255,255,255,255));
        }
    }
    
    private ProtocolVersion getProtocolVersionByName(String version) {
        Integer protocolId = VERSION_TO_PROTOCOL.get(version);
        if (protocolId != null) {
            try {
                return ProtocolVersion.getProtocol(protocolId);
            } catch (Exception e) {
                return null;
            }
        }
        return ProtocolVersion.getClosest(version);
    }
}

