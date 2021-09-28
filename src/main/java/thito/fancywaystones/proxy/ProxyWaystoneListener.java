package thito.fancywaystones.proxy;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.messaging.*;
import thito.fancywaystones.*;
import thito.fancywaystones.proxy.message.*;
import thito.fancywaystones.task.*;

public class ProxyWaystoneListener implements PluginMessageListener {
    @Override
    public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
        if ("fancywaystones:waystone".equals(s)) {
            Message message = Message.read(bytes);
            if (message instanceof WaystoneReloadMessage) {
                FancyWaystones.getPlugin().submitIO(() -> {
                    WaystoneManager.getManager().refresh(((WaystoneReloadMessage) message).getId());
                });
            } else if (message instanceof WaystoneUnloadMessage) {
                FancyWaystones.getPlugin().submitIO(() -> {
                    WaystoneData waystoneData = WaystoneManager.getManager().getData(((WaystoneUnloadMessage) message).getId());
                    if (waystoneData != null) {
                        waystoneData.destroy(((WaystoneUnloadMessage) message).getReason());
                    }
                });
            } else if (message instanceof TeleportMessage) {
                SerializableLocation target = ((TeleportMessage) message).getTarget();
                World world = Bukkit.getWorld(target.getWorldName());
                SerializableLocation source = ((TeleportMessage) message).getSource();
                if (world != null) {
                    Location location = new Location(world, target.getX(), target.getY(), target.getZ());
                    Player targetPlayer = Bukkit.getPlayer(((TeleportMessage) message).getPlayerUUID());
                    if (FancyWaystones.getPlugin().isEnabled() && targetPlayer != null) {
                        Bukkit.getScheduler().runTask(FancyWaystones.getPlugin(), new TeleportTask(
                                targetPlayer, location, FancyWaystones.getPlugin().getCheckRadius(), FancyWaystones.getPlugin().getCheckHeight(),
                                FancyWaystones.getPlugin().isForceTeleportation()) {
                            @Override
                            protected void done() {
                                if (!isSuccess() && source != null && ((TeleportMessage) message).isSendFeedback()) {
                                    player.sendPluginMessage(FancyWaystones.getPlugin(), "fancywaystones:waystone",
                                            new TeleportMessage(((TeleportMessage) message).getPlayerUUID(), false, null, source).write());
                                }
                            }
                        });
                    } else if (source != null && ((TeleportMessage) message).isSendFeedback()) {
                        player.sendPluginMessage(FancyWaystones.getPlugin(), "fancywaystones:waystone",
                                new TeleportMessage(((TeleportMessage) message).getPlayerUUID(), false, null, source).write());
                    }
                }
            }
        }
    }
}