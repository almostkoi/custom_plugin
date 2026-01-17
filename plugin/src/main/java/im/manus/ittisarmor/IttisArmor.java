package im.manus.ittisarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class IttisArmor extends JavaPlugin implements Listener, CommandExecutor {

    private File dataFile;
    private FileConfiguration dataConfig;
    private long serverUptimeSeconds = 0;
    private long lastTimerReset = 0;

    // Constant: 5 hours in seconds (18000)
    private final int REVEAL_INTERVAL = 18000;

    private final List<ArmorPiece> armorPieces = new ArrayList<>();
    private final String GUI_TITLE = "itti's Armor Set";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataFile();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ittisgive").setExecutor(this);

        initializeArmorPieces();
        loadData();

        new BukkitRunnable() {
            @Override
            public void run() {
                // TIMER LOGIC: Only tick if at least 1 player is online
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    serverUptimeSeconds++;
                    
                    // Attempt to fix broken locations every tick if needed
                    ensureLocationsLoaded();
                    
                    checkReveals();
                    applyArmorEffects();

                    if (serverUptimeSeconds % 60 == 0) {
                        saveData();
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        getLogger().info("IttisArmor enabled with Auto-Fix logic!");
    }

    @Override
    public void onDisable() {
        if (dataConfig != null) {
            saveData();
        }
        getLogger().info("IttisArmor disabled!");
    }

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void initializeArmorPieces() {
        armorPieces.add(new ArmorPiece("Helmet", Material.DIAMOND_HELMET));
        armorPieces.add(new ArmorPiece("Chestplate", Material.DIAMOND_CHESTPLATE));
        armorPieces.add(new ArmorPiece("Leggings", Material.DIAMOND_LEGGINGS));
        armorPieces.add(new ArmorPiece("Boots", Material.DIAMOND_BOOTS));
    }

    private void loadData() {
        serverUptimeSeconds = dataConfig.getLong("uptime", 0);
        lastTimerReset = dataConfig.getLong("lastTimerReset", 0);

        for (ArmorPiece piece : armorPieces) {
            String path = "pieces." + piece.name.toLowerCase();
            if (dataConfig.contains(path + ".world")) {
                piece.savedWorldUID = dataConfig.getString(path + ".world");
                piece.savedWorldName = dataConfig.getString(path + ".worldName");
                piece.savedX = dataConfig.getInt(path + ".x");
                piece.savedY = dataConfig.getInt(path + ".y");
                piece.savedZ = dataConfig.getInt(path + ".z");

                piece.revealed = dataConfig.getBoolean(path + ".revealed", false);
                piece.found = dataConfig.getBoolean(path + ".found", false);
                
                restoreLocation(piece);
            }
        }
    }

    private void restoreLocation(ArmorPiece piece) {
        if (piece.savedWorldUID != null) {
            World w = Bukkit.getWorld(UUID.fromString(piece.savedWorldUID));
            if (w == null && piece.savedWorldName != null) {
                w = Bukkit.getWorld(piece.savedWorldName);
            }
            
            if (w != null) {
                piece.location = new Location(w, piece.savedX, piece.savedY, piece.savedZ);
            }
        }
    }

    private void ensureLocationsLoaded() {
        for (ArmorPiece piece : armorPieces) {
            if (piece.location == null && piece.savedWorldUID != null) {
                restoreLocation(piece);
            }
        }
    }

    private void saveData() {
        dataConfig.set("uptime", serverUptimeSeconds);
        dataConfig.set("lastTimerReset", lastTimerReset);

        for (ArmorPiece piece : armorPieces) {
            String path = "pieces." + piece.name.toLowerCase();
            if (piece.location != null) {
                dataConfig.set(path + ".world", piece.location.getWorld().getUID().toString());
                dataConfig.set(path + ".worldName", piece.location.getWorld().getName());
                dataConfig.set(path + ".x", piece.location.getBlockX());
                dataConfig.set(path + ".y", piece.location.getBlockY());
                dataConfig.set(path + ".z", piece.location.getBlockZ());
            }
            dataConfig.set(path + ".revealed", piece.revealed);
            dataConfig.set(path + ".found", piece.found);
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        openGiveGUI(player);
        return true;
    }

    private void openGiveGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, Component.text(GUI_TITLE));
        int slot = 2;
        for (ArmorPiece piece : armorPieces) {
            gui.setItem(slot++, createArmorItem(piece));
        }
        player.openInventory(gui);
    }

    private ItemStack createArmorItem(ArmorPiece piece) {
        ItemStack item = new ItemStack(piece.material);

        if (item.getItemMeta() instanceof ArmorMeta meta) {
            meta.displayName(Component.text("itti's " + piece.name).color(NamedTextColor.GOLD));
            meta.setUnbreakable(true);
            meta.setTrim(new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.EYE));

            double armor = 0;
            double toughness = 3.0;
            double knockbackRes = 0.1;
            EquipmentSlotGroup slot = EquipmentSlotGroup.ANY;

            switch (piece.material) {
                case DIAMOND_HELMET -> { armor = 3; slot = EquipmentSlotGroup.HEAD; }
                case DIAMOND_CHESTPLATE -> { armor = 8; slot = EquipmentSlotGroup.CHEST; }
                case DIAMOND_LEGGINGS -> { armor = 6; slot = EquipmentSlotGroup.LEGS; }
                case DIAMOND_BOOTS -> { armor = 3; slot = EquipmentSlotGroup.FEET; }
            }

            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(
                    new NamespacedKey(this, "armor_" + piece.name.toLowerCase()),
                    armor, AttributeModifier.Operation.ADD_NUMBER, slot));
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(
                    new NamespacedKey(this, "toughness_" + piece.name.toLowerCase()),
                    toughness, AttributeModifier.Operation.ADD_NUMBER, slot));
            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(
                    new NamespacedKey(this, "kb_res_" + piece.name.toLowerCase()),
                    knockbackRes, AttributeModifier.Operation.ADD_NUMBER, slot));

            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);

            switch (piece.material) {
                case DIAMOND_HELMET -> {
                    meta.addEnchant(Enchantment.RESPIRATION, 3, true);
                    meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
                }
                case DIAMOND_LEGGINGS -> {
                    meta.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
                }
                case DIAMOND_BOOTS -> {
                    meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
                    meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
                    meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
                }
            }

            int modelData = getModelDataForMaterial(piece.material);
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isIttisItem(ItemStack item, Material material) {
        if (item == null || item.getType() != material) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Integer cmd = meta.getCustomModelData();
        if (cmd == null) return false;
        return cmd == getModelDataForMaterial(material);
    }
    
    // Checks if the item IS an armor piece, OR if it is a container holding one
    private boolean isRestrictedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // 1. Direct check
        if (isIttisItem(item, Material.DIAMOND_HELMET) ||
            isIttisItem(item, Material.DIAMOND_CHESTPLATE) ||
            isIttisItem(item, Material.DIAMOND_LEGGINGS) ||
            isIttisItem(item, Material.DIAMOND_BOOTS)) {
            return true;
        }

        // 2. Bundle check (Recursive)
        if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
            for (ItemStack content : bundleMeta.getItems()) {
                if (isRestrictedItem(content)) return true;
            }
        }

        // 3. Shulker Box check
        if (item.getItemMeta() instanceof BlockStateMeta blockStateMeta) {
            if (blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
                for (ItemStack content : shulkerBox.getInventory().getContents()) {
                    if (isRestrictedItem(content)) return true;
                }
            }
        }

        return false;
    }

    private int getModelDataForMaterial(Material material) {
        return switch (material) {
            case DIAMOND_HELMET -> 1001;
            case DIAMOND_CHESTPLATE -> 1002;
            case DIAMOND_LEGGINGS -> 1003;
            case DIAMOND_BOOTS -> 1004;
            default -> 0;
        };
    }

    private void applyArmorEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack helmet = player.getInventory().getHelmet();
            ItemStack chest = player.getInventory().getChestplate();
            ItemStack legs = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();

            // HELMET: Hero of the Village (Level 255)
            if (isIttisItem(helmet, Material.DIAMOND_HELMET)) {
                // Amplifier 255 usually reduces trades to 1 item
                player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 40, 255, false, false, true));
            }

            if (isIttisItem(chest, Material.DIAMOND_CHESTPLATE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, false, false, true));
            }
            if (isIttisItem(legs, Material.DIAMOND_LEGGINGS)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false, true));
            }
            if (isIttisItem(boots, Material.DIAMOND_BOOTS)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false, true));
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        // FOUND CHECK (Logic for finding chest in the world)
        if (event.getInventory().getHolder() instanceof Chest chest) {
            for (ArmorPiece piece : armorPieces) {
                if (!piece.found && piece.location != null && piece.location.equals(chest.getLocation())) {
                    piece.found = true;
                    lastTimerReset = serverUptimeSeconds; 

                    Bukkit.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD));
                    Bukkit.broadcast(Component.text("The " + piece.name + " has been found!", NamedTextColor.GREEN));
                    Bukkit.broadcast(Component.text("The next countdown begins now!", NamedTextColor.YELLOW));
                    Bukkit.broadcast(Component.text("--------------------------------", NamedTextColor.GOLD));

                    saveData();
                    break;
                }
            }
        } 
        
        // Removed old Villager recipe manual override logic. 
        // The Hero of the Village effect in applyArmorEffects now handles discounts.
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Handle GUI Logic
        if (event.getView().title().equals(Component.text(GUI_TITLE))) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);
                event.getWhoClicked().getInventory().addItem(event.getCurrentItem().clone());
                return;
            }
        }
        
        // Handle Ender Chest Restriction Logic
        if (event.getClickedInventory() != null && event.getWhoClicked() instanceof Player player) {
            Inventory topInv = player.getOpenInventory().getTopInventory();
            
            // Only check if an Ender Chest is open
            if (topInv.getType() == InventoryType.ENDER_CHEST) {
                
                // 1. Preventing placing item directly into Ender Chest (Cursor click)
                if (event.getClickedInventory().getType() == InventoryType.ENDER_CHEST) {
                    if (isRestrictedItem(event.getCursor())) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("You cannot store Itti's armor (or containers holding it) in an Ender Chest!", NamedTextColor.RED));
                    }
                    // Prevent swapping hotbar items into Ender Chest
                    if (event.getClick() == ClickType.NUMBER_KEY) {
                        ItemStack targetItem = player.getInventory().getItem(event.getHotbarButton());
                        if (isRestrictedItem(targetItem)) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("You cannot store Itti's armor in an Ender Chest!", NamedTextColor.RED));
                        }
                    }
                }

                // 2. Preventing Shift-Clicking from Player Inventory -> Ender Chest
                if (event.isShiftClick() && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                    if (isRestrictedItem(event.getCurrentItem())) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("You cannot store Itti's armor in an Ender Chest!", NamedTextColor.RED));
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (isRestrictedItem(event.getOldCursor())) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(Component.text("You cannot store Itti's armor in an Ender Chest!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() == World.Environment.NORMAL) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    loadData();
                    if (armorPieces.get(0).location == null) {
                        spawnAllChests(world);
                    }
                }
            }.runTaskLater(this, 100L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!event.getPlayer().isOnline()) return;

                ArmorPiece nextPiece = null;
                for (ArmorPiece piece : armorPieces) {
                    if (!piece.found) {
                        nextPiece = piece;
                        break;
                    }
                }

                if (nextPiece != null) {
                    long timeSinceReset = serverUptimeSeconds - lastTimerReset;
                    long remainingSeconds = REVEAL_INTERVAL - timeSinceReset;

                    if (remainingSeconds < 0) remainingSeconds = 0;

                    if (nextPiece.revealed) {
                        event.getPlayer().sendMessage(Component.text("IttisArmor: The " + nextPiece.name + " coordinates have been revealed!", NamedTextColor.RED));
                    } else {
                        String timeStr = formatTime(remainingSeconds);
                        Component message = Component.text("IttisArmor: ", NamedTextColor.GOLD)
                                .append(Component.text(timeStr + " left until " + nextPiece.name + " reveal.", NamedTextColor.YELLOW));
                        event.getPlayer().sendMessage(message);
                    }
                } else {
                    event.getPlayer().sendMessage(Component.text("IttisArmor: All pieces have been found!", NamedTextColor.GREEN));
                }
            }
        }.runTaskLater(this, 40L);
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }

    private void spawnAllChests(World world) {
        for (ArmorPiece piece : armorPieces) {
            if (piece.location == null) {
                respawnPiece(world, piece);
            }
        }
        saveData();
        getLogger().info("IttisArmor: Spawn scan complete.");
    }

    private void respawnPiece(World world, ArmorPiece piece) {
        Random random = new Random();
        Location spawn = world.getSpawnLocation();
        boolean placed = false;
        
        int attempts = 0;
        while (!placed && attempts < 10) {
            attempts++;
            int x = spawn.getBlockX() + random.nextInt(10001) - 5000;
            int z = spawn.getBlockZ() + random.nextInt(10001) - 5000;
            int y = random.nextInt(81) - 60;

            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isLoaded()) {
                chunk.load(true); 
            }

            Block block = world.getBlockAt(x, y, z);
            block.setType(Material.CHEST);
            if (block.getState() instanceof Chest chest) {
                chest.getInventory().addItem(createArmorItem(piece));
                piece.location = block.getLocation();
                placed = true;
                getLogger().info("Successfully spawned " + piece.name + " at " + x + ", " + y + ", " + z);
            }
        }
        
        if (!placed) {
            getLogger().warning("Failed to spawn " + piece.name + " after 10 attempts! Will retry next cycle.");
        }
    }

    private void checkReveals() {
        ArmorPiece currentTarget = null;
        for (ArmorPiece piece : armorPieces) {
            if (!piece.found) {
                currentTarget = piece;
                break;
            }
        }

        if (currentTarget == null) return;
        if (currentTarget.revealed) return;

        long timeSinceReset = serverUptimeSeconds - lastTimerReset;

        if (timeSinceReset >= REVEAL_INTERVAL) {
            if (currentTarget.location == null || currentTarget.location.getWorld() == null) {
                getLogger().warning(currentTarget.name + " reveal time reached but location is null. Force respawning...");
                World overworld = Bukkit.getWorld("world");
                if (overworld == null && !Bukkit.getWorlds().isEmpty()) {
                    overworld = Bukkit.getWorlds().get(0);
                }
                
                if (overworld != null) {
                    respawnPiece(overworld, currentTarget);
                    saveData();
                }
            }

            if (currentTarget.location != null && currentTarget.location.getWorld() != null) {
                currentTarget.revealed = true;
                broadcastReveal(currentTarget);
                saveData();
            }
        }
    }

    private void broadcastReveal(ArmorPiece piece) {
        Component message = Component.text("[itti's Armor] ", NamedTextColor.GOLD)
                .append(Component.text("The " + piece.name + " coords: X:" +
                        piece.location.getBlockX() + " Y:" +
                        piece.location.getBlockY() + " Z:" +
                        piece.location.getBlockZ(), NamedTextColor.RED));
        Bukkit.broadcast(message);
    }

    private static class ArmorPiece {
        String name;
        Material material;
        Location location;
        boolean revealed = false;
        boolean found = false;

        String savedWorldUID;
        String savedWorldName;
        int savedX, savedY, savedZ;

        ArmorPiece(String name, Material material) {
            this.name = name;
            this.material = material;
        }
    }
}
