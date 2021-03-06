package thito.fancywaystones;

import org.bukkit.*;
import org.bukkit.entity.*;
import thito.fancywaystones.location.*;
import thito.fancywaystones.proxy.*;
import thito.fancywaystones.ui.*;

import java.util.*;

public class WaystoneData {
    private Set<PlayerData> attached = new HashSet<>();
    private WaystoneBlock waystoneBlock;

    private UUID uuid;
    private String ownerName;
    private UUID ownerUUID;
    private String name;
    private WaystoneLocation location;
    private WaystoneType type;
    private WaystoneModel model;
    private World.Environment environment;
    private Set<WaystoneMember> members = new LinkedHashSet<>();
    private Set<AttachedMenu> openedMenus = Collections.newSetFromMap(new WeakHashMap<>());
    private Set<WaystoneMember> blacklist = new LinkedHashSet<>();
    private WaystoneStatistics statistics = new WaystoneStatistics();

    public WaystoneData(UUID uuid, WaystoneType type, WaystoneModel model, World.Environment environment) {
        this.uuid = uuid;
        this.type = type;
        this.model = model;
        this.environment = environment;
    }

    public void directValidateBlock() {
        if (getWaystoneBlock() == null) {
            WaystoneManager.getManager().placeWaystone(this, ((LocalLocation) location).getLocation());
        }
    }

    public void validateBlock() {
        if (location instanceof LocalLocation && FancyWaystones.getPlugin().isEnabled()) {
            Bukkit.getScheduler().runTask(FancyWaystones.getPlugin(), () -> {
                int chunkX = location.getBlockX() >> 4;
                int chunkZ = location.getBlockZ() >> 4;
                World world = ((LocalLocation) location).getLocation().getWorld();
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    FancyWaystones.getPlugin().submitIO(this::directValidateBlock);
                } else {
                    WaystoneManager.getManager().putBlockData(((LocalLocation) location).getLocation(), getUUID());
                }
            });
        }
    }

    public void setLocation(WaystoneLocation location) {
        this.location = location;
    }

    public synchronized WaystoneBlock getWaystoneBlock() {
        return waystoneBlock;
    }

    public synchronized Set<PlayerData> getAttached() {
        return attached;
    }

    protected void _setWaystoneBlock(WaystoneBlock block) {
        this.waystoneBlock = block;
    }

    public synchronized void setWaystoneBlock(WaystoneBlock waystoneBlock) {
        FancyWaystones.checkIOThread();
        if (location instanceof LocalLocation) {
            if (waystoneBlock == null) {
                WaystoneManager.getManager().removeBlockData(((LocalLocation) location).getLocation(), getUUID());
            } else if (this.waystoneBlock == null) {
                WaystoneManager.getManager().putBlockData(((LocalLocation) location).getLocation(), getUUID());
            }
        }
        _setWaystoneBlock(waystoneBlock);
    }

    public synchronized void addAttached(PlayerData playerData) {
        attached.add(playerData);
    }

    public synchronized void removeAttached(PlayerData playerData) {
        attached.remove(playerData);
    }

    public synchronized boolean shouldUnload() {
        return attached.isEmpty() && waystoneBlock == null && !type.isAlwaysLoaded();
    }

    public void destroy(String reason) {
        FancyWaystones.checkIOThread();
        if (type.isActivationRequired()) {
            for (PlayerData d : attached) {
                d.removeWaystone(getUUID());
                Player online = d.getPlayer();
                if (online != null) {
                    Placeholder placeholder = new Placeholder()
                            .putContent(Placeholder.PLAYER, online)
                            .putContent(Placeholder.WAYSTONE, this)
                            .put("reason", ph -> reason);
                    online.sendMessage(placeholder.replace("{language.destroyed}"));
                }
            }
        }

        ProxyWaystone pw = FancyWaystones.getPlugin().getProxyWaystone();
        if (pw != null) {
            if (type.isActivationRequired()) {
                pw.dispatchWaystoneDestroy(getUUID(), reason);
            } else if (type.isAlwaysListed()) {
                pw.dispatchWaystoneUnload(getUUID());
            }
        }

        WaystoneStorage storage = WaystoneManager.getManager().getStorage();
        String name = getName();
        if (name != null) {
            storage.removeName(getType().getUniqueNamesContext(), name);
        }

        getOpenedMenus().forEach((AttachedMenu::close));

        storage.removeWaystoneData(getUUID());

        WaystoneManager.getManager().directUnloadData(this);
        if (waystoneBlock != null) {
            waystoneBlock.destroyModel();
            setWaystoneBlock(null);
        }

    }

    public WaystoneModel getModel() {
        return model;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public WaystoneStatistics getStatistics() {
        return statistics;
    }

    public Set<WaystoneMember> getMembers() {
        return members;
    }

    public Set<WaystoneMember> getBlacklist() {
        return blacklist;
    }

    public void addMember(Player member) {
        WaystoneMember m = new WaystoneMember(member.getUniqueId(), member.getName());
        if (!members.contains(m)) {
            members.add(m);
            attemptSave();
        }
    }

    public void removeMember(UUID member) {
        PlayerData playerData = WaystoneManager.getManager().getPlayerData(null, member);
        playerData.removeWaystone(getUUID());
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public void claim(Player owner) {
        ownerName = owner.getName();
        ownerUUID = owner.getUniqueId();
        attemptSave();
    }

    public Set<AttachedMenu> getOpenedMenus() {
        return openedMenus;
    }

    public void attemptSave() {
        if (location != null) {
            FancyWaystones.getPlugin().submitIO(() -> {
                WaystoneManager.getManager().saveWaystone(this);
                ProxyWaystone pw = FancyWaystones.getPlugin().getProxyWaystone();
                if (pw != null) pw.dispatchWaystoneReload(getUUID());
            });
        }
    }

    public void setModel(WaystoneModel model) {
        this.model = model;
    }

    @Override
    public String toString() {
        return "WaystoneData{" +
                "uuid=" + uuid +
                ", ownerName='" + ownerName + '\'' +
                ", ownerUUID=" + ownerUUID +
                ", name='" + name + '\'' +
                ", location=" + location +
                ", type=" + type +
                ", environment=" + environment +
                '}';
    }

    public World.Environment getEnvironment() {
        return environment;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        attemptSave();
    }

    public WaystoneLocation getLocation() {
        return location;
    }

    public WaystoneType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WaystoneData)) return false;
        WaystoneData that = (WaystoneData) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    public void teleport(WaystoneData source, Player player) {
        if (source != null) {
            source.getStatistics().setTotalVisits(source.getStatistics().getTotalVisits() + 1);
            source.getStatistics().setLastVisit(System.currentTimeMillis());
            source.attemptSave();
        }

        getStatistics().setTotalVisitors(getStatistics().getTotalVisitors() + 1);
        getStatistics().setLastVisited(System.currentTimeMillis());
        attemptSave();

        location.transport(player, source, this, state -> {
            if (state == TeleportState.SUCCESS) {
                player.setNoDamageTicks((int) (player.getNoDamageTicks() + FancyWaystones.getPlugin().getNoDamageTicks()));
                FancyWaystones.getPlugin().postTeleport("Waystone", player, source, this);
            } else if (state == TeleportState.UNSAFE) {
                player.sendMessage(new Placeholder().putContent(Placeholder.WAYSTONE, this).replace("{language.unsafe-waystone}"));
            } else if (state == TeleportState.INVALID) {
                player.sendMessage(new Placeholder().putContent(Placeholder.WAYSTONE, this).replace("{language.invalid-waystone}"));
            }
        });
    }

}
