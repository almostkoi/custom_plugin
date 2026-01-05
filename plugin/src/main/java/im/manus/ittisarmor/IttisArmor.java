package im.manus.ittisarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final List<ArmorPiece> armorPieces = new ArrayList<>();
    private final String GUI_TITLE = "itti's Armor Set";
    private final String ITEM_MODEL_NAMESPACE = "minecraft:ittis_";

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
                serverUptimeSeconds++;
                checkReveals();
                applyArmorEffects();
                if (serverUptimeSeconds % 60 == 0) {
                    saveData();
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        getLogger().info("IttisArmor enabled for 1.21.11!");
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
        armorPieces.add(new ArmorPiece("Helmet", Material.DIAMOND_HELMET, 5));
        armorPieces.add(new ArmorPiece("Chestplate", Material.DIAMOND_CHESTPLATE, 10));
        armorPieces.add(new ArmorPiece("Leggings", Material.DIAMOND_LEGGINGS, 15));
        armorPieces.add(new ArmorPiece("Boots", Material.DIAMOND_BOOTS, 20));
    }

    private void loadData() {
        serverUptimeSeconds = dataConfig.getLong("uptime", 0);
        for (ArmorPiece piece : armorPieces) {
            String path = "pieces." + piece.name.toLowerCase();
            if (dataConfig.contains(path + ".x")) {
                String worldUid = dataConfig.getString(path + ".world");
                if (worldUid != null) {
                    piece.location = new Location(
                            Bukkit.getWorld(UUID.fromString(worldUid)),
                            dataConfig.getInt(path + ".x"),
                            dataConfig.getInt(path + ".y"),
                            dataConfig.getInt(path + ".z")
                    );
                }
                piece.revealed = dataConfig.getBoolean(path + ".revealed", false);
            }
        }
    }

    private void saveData() {
        dataConfig.set("uptime", serverUptimeSeconds);
        for (ArmorPiece piece : armorPieces) {
            if (piece.location != null) {
                String path = "pieces." + piece.name.toLowerCase();
                dataConfig.set(path + ".world", piece.location.getWorld().getUID().toString());
                dataConfig.set(path + ".x", piece.location.getBlockX());
                dataConfig.set(path + ".y", piece.location.getBlockY());
                dataConfig.set(path + ".z", piece.location.getBlockZ());
                dataConfig.set(path + ".revealed", piece.revealed);
            }
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
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("itti's " + piece.name).color(NamedTextColor.GOLD));
            meta.setUnbreakable(true);

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

            item.setItemMeta(meta);
        }
            // Use CustomModelData so resource packs can override the diamond model reliably
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
            ItemStack chest = player.getInventory().getChestplate();
            ItemStack legs = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();

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
        if (event.getInventory().getHolder() instanceof Villager villager) {
            Player player = (Player) event.getPlayer();
            if (isIttisItem(player.getInventory().getHelmet(), Material.DIAMOND_HELMET)) {
                List<MerchantRecipe> recipes = new ArrayList<>();
                for (MerchantRecipe recipe : villager.getRecipes()) {
                    MerchantRecipe newRecipe = new MerchantRecipe(recipe.getResult(), recipe.getMaxUses());
                    newRecipe.setExperienceReward(recipe.hasExperienceReward());
                    newRecipe.setVillagerExperience(recipe.getVillagerExperience());
                    newRecipe.setPriceMultiplier(recipe.getPriceMultiplier());
                    newRecipe.setDemand(recipe.getDemand());
                    newRecipe.setSpecialPrice(recipe.getSpecialPrice());

                    for (ItemStack ingredient : recipe.getIngredients()) {
                        ItemStack newIngredient = ingredient.clone();
                        newIngredient.setAmount(1);
                        newRecipe.addIngredient(newIngredient);
                    }
                    recipes.add(newRecipe);
                }
                villager.setRecipes(recipes);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(Component.text(GUI_TITLE))) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);
                event.getWhoClicked().getInventory().addItem(event.getCurrentItem().clone());
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
                    if (armorPieces.get(0).location == null) {
                        spawnChests(world);
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
                    if (!piece.revealed) {
                        nextPiece = piece;
                        break;
                    }
                }

                if (nextPiece != null) {
                    long targetSeconds = (long) nextPiece.revealHour * 3600;
                    long remainingSeconds = targetSeconds - serverUptimeSeconds;
                    if (remainingSeconds < 0) remainingSeconds = 0;

                    String timeStr = formatTime(remainingSeconds);
                    Component message = Component.text("IttisArmor: ", NamedTextColor.GOLD)
                            .append(Component.text(timeStr + " is left till the next armor piece cords are revealed", NamedTextColor.GOLD));
                    event.getPlayer().sendMessage(message);
                }
            }
        }.runTaskLater(this, 40L);
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(" hours ");
        if (m > 0 || h > 0) sb.append(m).append(" minutes ");
        sb.append(s).append(" seconds");
        return sb.toString().trim();
    }

    private void spawnChests(World world) {
        Random random = new Random();
        Location spawn = world.getSpawnLocation();

        for (ArmorPiece piece : armorPieces) {
            boolean placed = false;
            while (!placed) {
                int x = spawn.getBlockX() + random.nextInt(10001) - 5000;
                int z = spawn.getBlockZ() + random.nextInt(10001) - 5000;
                int y = random.nextInt(81) - 60;

                Block block = world.getBlockAt(x, y, z);
                block.setType(Material.CHEST);
                if (block.getState() instanceof Chest chest) {
                    chest.getInventory().addItem(createArmorItem(piece));
                    piece.location = block.getLocation();
                    placed = true;
                }
            }
        }
        saveData();
        getLogger().info("Spawned 4 chests for itti's armor set.");
    }

    private void checkReveals() {
        long uptimeHours = serverUptimeSeconds / 3600;
        for (ArmorPiece piece : armorPieces) {
            if (!piece.revealed && uptimeHours >= piece.revealHour && piece.location != null) {
                piece.revealed = true;
                broadcastReveal(piece);
                saveData();
            }
        }
    }

    private void broadcastReveal(ArmorPiece piece) {
        Component message = Component.text("[itti's Armor] ", NamedTextColor.GOLD)
                .append(Component.text("The " + piece.name + " has been found at X: " + 
                        piece.location.getBlockX() + ", Y: " + 
                        piece.location.getBlockY() + ", Z: " + 
                        piece.location.getBlockZ() + "!", NamedTextColor.GOLD));
        Bukkit.broadcast(message);
    }

    private static class ArmorPiece {
        String name;
        Material material;
        int revealHour;
        Location location;
        boolean revealed = false;

        ArmorPiece(String name, Material material, int revealHour) {
            this.name = name;
            this.material = material;
            this.revealHour = revealHour;
        }
    }
}
