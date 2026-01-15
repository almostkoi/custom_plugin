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
                // CHANGE: Only run logic if there is at least 1 player online
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
