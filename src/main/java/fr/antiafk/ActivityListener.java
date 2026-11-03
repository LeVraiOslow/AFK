package fr.antiafk;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public final class ActivityListener implements Listener {

    private final AfkManager m;

    public ActivityListener(AfkManager m) { this.m = m; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        // Les téléportations (ex: /warp zoneafk) ne sont PAS de l'activité
        if (e instanceof PlayerTeleportEvent) { m.onTeleport(e.getPlayer(), e.getTo()); return; }
        m.onMove(e.getPlayer(), e.getFrom(), e.getTo());
    }

    // Les passagers de véhicules ne déclenchent pas PlayerMoveEvent
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent e) {
        for (Entity passenger : e.getVehicle().getPassengers()) {
            if (passenger instanceof Player p) m.onMove(p, e.getFrom(), e.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if (m.act("chat")) m.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (m.act("commands")) m.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        if (m.act("interact")) m.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (m.act("blocks")) m.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (m.act("blocks")) m.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventory(InventoryClickEvent e) {
        if (m.act("inventory")) m.touch(e.getWhoClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent e) {
        if (m.act("held-item")) m.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { m.touch(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { m.remove(e.getPlayer().getUniqueId()); }
}
