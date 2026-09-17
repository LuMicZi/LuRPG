package com.lumiczi.lurpg.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener for custom InventoryHolder-based GUIs (EnhanceGUI, SocketGUI).
 * <p>
 * These GUIs use the InventoryHolder pattern instead of the GUIManager
 * session pattern because they need to support item placement and
 * drag-and-drop interactions (the standard GUIManager cancels all clicks).
 * </p>
 */
public class CustomGUIListener implements Listener {

    /**
     * Handles inventory click events for custom holder-based GUIs.
     *
     * @param event the click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null) {
            return;
        }

        InventoryHolder holder = topInv.getHolder();
        if (holder instanceof EnhanceGUI enhanceGUI) {
            enhanceGUI.handleClick(event);
        } else if (holder instanceof SocketGUI socketGUI) {
            socketGUI.handleClick(event);
        }
    }

    /**
     * Handles inventory close events for custom holder-based GUIs.
     *
     * @param event the close event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Inventory inv = event.getInventory();
        if (inv == null) {
            return;
        }

        InventoryHolder holder = inv.getHolder();
        if (holder instanceof EnhanceGUI enhanceGUI) {
            enhanceGUI.handleClose(event);
        } else if (holder instanceof SocketGUI socketGUI) {
            socketGUI.handleClose(event);
        }
    }
}
