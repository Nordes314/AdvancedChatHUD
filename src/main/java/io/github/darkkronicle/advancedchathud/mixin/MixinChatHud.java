/*
 * Copyright (C) 2021 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud.mixin;

import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchathud.HudChatMessage;
import io.github.darkkronicle.advancedchathud.HudChatMessageHolder;
import io.github.darkkronicle.advancedchathud.config.HudConfigStorage;
import io.github.darkkronicle.advancedchathud.gui.WindowManager;
import io.github.darkkronicle.advancedchathud.itf.IChatHud;
import io.github.darkkronicle.advancedchathud.tabs.AbstractChatTab;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatComponent.class, priority = 1050)
@Environment(EnvType.CLIENT)
public abstract class MixinChatHud implements IChatHud {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    @Unique
    private AbstractChatTab tab;

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract double getScale();

    @Shadow
    public abstract boolean isChatFocused();

    @Shadow
    public abstract void scrollChat(int amount);

    @Shadow
    public abstract int getHeight();

    @Inject(at = @At("HEAD"), method = "scrollChat", cancellable = true)
    private void scrollChat(int amount, CallbackInfo ci) {
        // Only scroll if nothing is focused
        if (WindowManager.getInstance().getSelected() != null) {
            ci.cancel();
        }
    }

    // 26.2: render(...) became extractRenderState(...), and the two trailing booleans
    // collapsed into a DisplayMode enum plus one flag.
    @Inject(
            at = @At("HEAD"),
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/client/gui/Font;III"
                    + "Lnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            cancellable = true)
    private void render(
            GuiGraphicsExtractor context,
            Font textRenderer,
            int currentTick,
            int mouseX,
            int mouseY,
            ChatComponent.DisplayMode displayMode,
            boolean refreshed,
            CallbackInfo ci) {
        // Ignore rendering vanilla chat if disabled
        if (!HudConfigStorage.General.VANILLA_HUD.config.getBooleanValue()) {
            ci.cancel();
        }
    }

    public AbstractChatTab getTab() {
        return tab;
    }

    public void setTab(AbstractChatTab tab) {
        this.tab = tab;
        this.allMessages.clear();
        this.trimmedMessages.clear();

        List<HudChatMessage> messages = HudChatMessageHolder.getInstance().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            addMessage(messages.get(i));
        }
    }

    @Override
    public void removeMessage(ChatMessage remove) {
        // Reset messages that exist
        setTab(this.tab);
    }

    @Override
    public void addMessage(HudChatMessage hudMsg) {
        if (tab == null || !hudMsg.getTabs().contains(tab)) {
            return;
        }
        if (HudConfigStorage.General.VANILLA_HUD.config.getBooleanValue()) {
            tab.resetUnread();
        }

        int width = Mth.floor((double) this.getWidth() / this.getScale());

        ChatMessage msg = hudMsg.getMessage();

        List<FormattedCharSequence> list =
                ComponentRenderUtils.wrapComponents(
                        msg.getDisplayText(), width, this.minecraft.font);

        // GuiMessage.Line now references the parent GuiMessage rather than repeating its
        // fields, so build the message once and hand it to every line.
        GuiMessage guiMessage = new GuiMessage(
                msg.getCreationTick(),
                msg.getDisplayText(),
                msg.getSignature(),
                GuiMessageSource.SYSTEM_CLIENT,
                msg.getIndicator());

        FormattedCharSequence orderedText;
        for (Iterator<FormattedCharSequence> text = list.iterator();
                text.hasNext();
                this.trimmedMessages.addFirst(
                        new GuiMessage.Line(guiMessage, orderedText, !text.hasNext()))) {
            orderedText = text.next();
            if (this.isChatFocused() && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(1);
            }
        }

        while (this.trimmedMessages.size()
                > HudConfigStorage.General.STORED_LINES.config.getIntegerValue()) {
            this.trimmedMessages.removeLast();
        }

        this.allMessages.addFirst(guiMessage);
        while (this.allMessages.size()
                > HudConfigStorage.General.STORED_LINES.config.getIntegerValue()) {
            this.allMessages.removeLast();
        }
    }

    @Shadow
    public abstract void clearMessages(boolean clearHistory);


    @Override
    public boolean isOver(double mouseX, double mouseY) {
        double minX = 4 - (4 * getScale());
        double maxX = 4 + (getWidth() + 4 * getScale());

        mouseY = (minecraft.getWindow().getGuiScaledHeight() - mouseY - 40) / getScale();
        return mouseX >= minX && mouseX < maxX && mouseY >= 0 && mouseY < getHeight();
    }
}
