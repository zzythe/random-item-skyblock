package dev.farskies.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** A faint green screen-edge fade while standing on the market's own land. No blocks, labels or collision. */
public final class MarketBoundary {
    private static float fade;
    private static long last;
    public static void hud(GuiGraphics gui,DeltaTracker partial) {
        var mc=Minecraft.getInstance();var s=CampaignView.status;long now=System.currentTimeMillis();
        float step=last==0?0:Math.min(1,(now-last)/500f);last=now;
        boolean safe=s!=null && s.state()!=3 && s.safe() && mc.player!=null && !mc.options.hideGui;
        fade=safe?Math.min(1,fade+step):Math.max(0,fade-step);
        if(fade<=0)return;
        int w=gui.guiWidth(),h=gui.guiHeight();float t=Math.min(w,h)*.14f;int alpha=(int)(70*fade);
        Matrix4f m=gui.pose().last().pose();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        // Mitred trapezoids, outer edge tinted and inner edge transparent, so corners never double up.
        quad(b,m,0,0,w,0,w-t,t,t,t,alpha);
        quad(b,m,w,h,0,h,t,h-t,w-t,h-t,alpha);
        quad(b,m,0,h,0,0,t,t,t,h-t,alpha);
        quad(b,m,w,0,w,h,w-t,h-t,w-t,t,alpha);
        var mesh=b.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.disableBlend();
    }
    private static void quad(BufferBuilder b,Matrix4f m,float ox1,float oy1,float ox2,float oy2,float ix2,float iy2,float ix1,float iy1,int alpha) {
        b.addVertex(m,ox1,oy1,0).setColor(105,205,145,alpha);b.addVertex(m,ox2,oy2,0).setColor(105,205,145,alpha);
        b.addVertex(m,ix2,iy2,0).setColor(105,205,145,0);b.addVertex(m,ix1,iy1,0).setColor(105,205,145,0);
    }
}
