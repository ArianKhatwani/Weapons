package ru.ultimateartifacts;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;

public final class UltimateArtifacts extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey artifactKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final Map<UUID, Long> phoenixUsed = new HashMap<>();

    private static final class ScytheMenu implements InventoryHolder {
        private final Inventory inventory;
        ScytheMenu() { inventory = Bukkit.createInventory(this, 9, ChatColor.DARK_PURPLE + "Серп пустоты"); }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig(); artifactKey = new NamespacedKey(this, "artifact");
        Objects.requireNonNull(getCommand("artifacts")).setExecutor(this);
        Objects.requireNonNull(getCommand("artifacts")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("UltimateArtifacts enabled. Resource pack is in the resourcepack folder.");
    }

    private static final String[] IDS = {"void_scythe","thunder_blade","blood_dagger","guardian_shield","phoenix_feather","rift_crystal","time_hourglass","berserker_heart","frost_rune","summoning_bell"};
    private static final String[] NAMES = {"Серп пустоты","Громовой клинок","Кинжал кровопийцы","Щит хранителя","Перо феникса","Кристалл разлома","Песочные часы времени","Сердце берсерка","Руна ледяной тюрьмы","Колокол призыва"};
    private static final Material[] BASES = {Material.NETHERITE_HOE,Material.NETHERITE_SWORD,Material.NETHERITE_SWORD,Material.SHIELD,Material.FEATHER,Material.AMETHYST_SHARD,Material.CLOCK,Material.HEART_OF_THE_SEA,Material.BLUE_ICE,Material.BELL};

    private ItemStack makeItem(int index) {
        ItemStack item = new ItemStack(BASES[index]); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + NAMES[index]);
        meta.setLore(List.of(ChatColor.DARK_PURPLE + "Артефакт Бездны", ChatColor.GRAY + abilityHint(index), ChatColor.DARK_GRAY + "Выдаётся только командой администратора"));
        meta.setUnbreakable(true); meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, IDS[index]);
        meta.setItemModel(new NamespacedKey("ultimateartifacts", IDS[index])); item.setItemMeta(meta); return item;
    }
    private String abilityHint(int i) { return switch(i) { case 0 -> "ПКМ: открыть меню способностей | Выбор в меню мышью"; case 1 -> "Цепная молния при ударе"; case 2 -> "Похищает часть здоровья при ударе"; case 3 -> "ПКМ: защитный купол"; case 4 -> "Однократное спасение от смерти"; case 5 -> "ПКМ: скачок сквозь разлом"; case 6 -> "ПКМ: замедление времени вокруг"; case 7 -> "ПКМ: ярость ценой здоровья"; case 8 -> "ПКМ: ледяная тюрьма"; default -> "ПКМ: призыв каменного стража"; }; }
    private String id(ItemStack item) { if(item==null || !item.hasItemMeta()) return null; return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING); }
    private boolean has(Player p, String id) { for(ItemStack it:p.getInventory().getContents()) if(id.equals(id(it))) return true; return false; }
    private boolean ready(Player p, String key, int seconds) { long now=System.currentTimeMillis(), end=cooldowns.getOrDefault(UUID.nameUUIDFromBytes((p.getUniqueId()+key).getBytes()),0L); if(end>now){p.sendActionBar(ChatColor.DARK_PURPLE+"Перезарядка: "+((end-now+999)/1000)+"с"); return false;} cooldowns.put(UUID.nameUUIDFromBytes((p.getUniqueId()+key).getBytes()),now+seconds*1000L); return true; }
    private List<LivingEntity> targets(Location center,double radius,Player owner) { List<LivingEntity> out=new ArrayList<>(); for(Entity e:center.getWorld().getNearbyEntities(center,radius,radius,radius)) if(e instanceof LivingEntity l && l!=owner && !(l instanceof ArmorStand) && (owner.getWorld()!=l.getWorld() || !owner.equals(l))) out.add(l); return out; }
    private void particles(Location l,Particle p,int count,double spread,double speed) { l.getWorld().spawnParticle(p,l,count,spread,spread,spread,speed); }
    private void damage(LivingEntity e,Player p,double amount) { e.damage(amount,p); }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND) return; String id=id(e.getItem()); if(id==null) return;
        if(e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true); Player p=e.getPlayer();
        switch(id) {
            case "void_scythe" -> openScytheMenu(p);
            case "guardian_shield" -> shield(p);
            case "rift_crystal" -> riftDash(p);
            case "time_hourglass" -> timeStop(p);
            case "berserker_heart" -> berserk(p);
            case "frost_rune" -> frostPrison(p);
            case "summoning_bell" -> summon(p);
        }
    }
    private void openScytheMenu(Player p) {
        ScytheMenu menu = new ScytheMenu();
        menu.inventory.setItem(0, menuItem(Material.NETHERITE_SWORD, "Разрез пустоты", "Волна урона и притяжения", "Перезарядка: 5 сек."));
        menu.inventory.setItem(2, menuItem(Material.CRYING_OBSIDIAN, "Гравитационная яма", "Стягивает врагов в одну точку", "Перезарядка: 18 сек."));
        menu.inventory.setItem(4, menuItem(Material.ENDER_PEARL, "Теневой рывок", "Рывок сквозь врагов", "Перезарядка: 10 сек."));
        menu.inventory.setItem(6, menuItem(Material.NETHER_STAR, "Абсолютная пустота", "Мощный финальный взрыв", "Перезарядка: 75 сек."));
        menu.inventory.setItem(8, menuItem(Material.BARRIER, "Закрыть", "Нажми, чтобы закрыть меню"));
        p.openInventory(menu.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.7f);
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler public void onScytheMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ScytheMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        switch (e.getRawSlot()) {
            case 0 -> { p.closeInventory(); voidSlash(p); }
            case 2 -> { p.closeInventory(); gravityWell(p); }
            case 4 -> { p.closeInventory(); shadowDash(p); }
            case 6 -> { p.closeInventory(); absoluteVoid(p); }
            case 8 -> p.closeInventory();
            default -> { }
        }
    }

    private void voidSlash(Player p) { if(!ready(p,"slash",5))return; Location eye=p.getEyeLocation(); Vector dir=eye.getDirection().normalize(); World w=p.getWorld(); w.playSound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,1.2f,0.65f); w.playSound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,1f,0.5f);
        new BukkitRunnable(){int step=0; public void run(){ if(step++>=12){cancel();return;} Location c=eye.clone().add(dir.clone().multiply(step*0.9)); w.spawnParticle(Particle.SOUL_FIRE_FLAME,c,35,0.8,0.45,0.8,0.02); w.spawnParticle(Particle.PORTAL,c,25,0.7,0.5,0.7,0.25); for(LivingEntity t:targets(c,1.45,p)){damage(t,p,config().getDouble("scythe.slash-damage",7.0)); Vector pull=p.getLocation().toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.65); pull.setY(0.18); t.setVelocity(pull);}}}.runTaskTimer(this,0L,1L);
    }
    private void gravityWell(Player p) { if(!ready(p,"well",18))return; Location c=p.getTargetBlockExact(18)==null?p.getLocation().add(p.getLocation().getDirection().multiply(8)):p.getTargetBlockExact(18).getLocation().add(0.5,1,0); p.getWorld().playSound(c,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,2f,0.5f); new BukkitRunnable(){int tick=0; public void run(){if(tick++>=80){p.getWorld().createExplosion(c,0f,false,false);cancel();return;} p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,c,30,2,1,2,0.1); p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,c,12,1.6,0.7,1.6,0.01); for(LivingEntity t:targets(c,5,p)){Vector v=c.toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.35);v.setY(0.12);t.setVelocity(v);if(tick%20==0)damage(t,p,2.0);}}}.runTaskTimer(this,0L,1L); }
    private void shadowDash(Player p) { if(!ready(p,"dash",10))return; Vector d=p.getLocation().getDirection().normalize(); Location start=p.getLocation(); p.setVelocity(d.multiply(1.8).setY(0.25)); p.getWorld().playSound(start,Sound.ENTITY_ENDERMAN_TELEPORT,1.3f,0.6f); new BukkitRunnable(){int n=0; public void run(){if(n++>8){cancel();return;} Location l=p.getLocation();particles(l,Particle.PORTAL,45,0.8,0.2); for(LivingEntity t:targets(l,1.5,p))damage(t,p,3.0);}}.runTaskTimer(this,0,1); }
    private void absoluteVoid(Player p) { if(!ready(p,"ultimate",75))return; Location c=p.getTargetBlockExact(24)==null?p.getLocation().add(p.getLocation().getDirection().multiply(12)):p.getTargetBlockExact(24).getLocation().add(0.5,1,0); World w=p.getWorld(); w.playSound(c,Sound.ENTITY_WARDEN_SONIC_BOOM,2f,0.55f); new BukkitRunnable(){int t=0; public void run(){if(t++>=45){for(LivingEntity e:targets(c,9,p)){damage(e,p,config().getDouble("scythe.ultimate-damage",24));e.setVelocity(c.toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.2).setY(0.5));}w.spawnParticle(Particle.EXPLOSION_EMITTER,c,2);w.spawnParticle(Particle.SOUL_FIRE_FLAME,c,250,5,3,5,0.08);w.playSound(c,Sound.ENTITY_GENERIC_EXPLODE,2f,0.7f);cancel();return;} double r=0.25+t*0.19; for(int i=0;i<24;i++){double a=i*Math.PI/12; Location q=c.clone().add(Math.cos(a)*r,Math.sin(t*0.15)*0.5,Math.sin(a)*r);w.spawnParticle(Particle.REVERSE_PORTAL,q,2,0,0,0,0.01);} if(t%10==0)w.spawnParticle(Particle.SONIC_BOOM,c,1);}}.runTaskTimer(this,0,1); }

    private void shield(Player p){if(!ready(p,"shield",35))return;p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,3));p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,4));p.getWorld().playSound(p.getLocation(),Sound.ITEM_SHIELD_BLOCK,1.5f,0.7f);new BukkitRunnable(){int t=0;public void run(){if(t++>=100){cancel();return;}Location l=p.getLocation().add(0,1,0);for(int i=0;i<24;i++){double a=i*Math.PI/12;p.getWorld().spawnParticle(Particle.END_ROD,l.clone().add(Math.cos(a)*1.5,Math.sin(t*0.08),Math.sin(a)*1.5),1);}}}.runTaskTimer(this,0,2);}
    private void riftDash(Player p){if(!ready(p,"rift",12))return;Location from=p.getLocation();Vector d=from.getDirection().normalize();Location to=from.clone().add(d.multiply(15));while(to.getBlock().isSolid()||to.clone().add(0,1,0).getBlock().isSolid()){to.subtract(from.getDirection().normalize());if(to.distanceSquared(from)<4)return;}p.getWorld().spawnParticle(Particle.PORTAL,from.add(0,1,0),100,1,1,1,0.4);p.teleport(to);p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,to.add(0,1,0),100,1,1,1,0.4);p.getWorld().playSound(to,Sound.ENTITY_ENDERMAN_TELEPORT,1.5f,0.8f);}
    private void timeStop(Player p){if(!ready(p,"time",45))return;for(LivingEntity t:targets(p.getLocation(),8,p)){t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,6));t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,100,2));}p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,2f,0.5f);p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,1));}
    private void berserk(Player p){if(!ready(p,"rage",30))return;p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,160,2));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,160,1));p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,160,0));p.setHealth(Math.max(1,p.getHealth()-Math.min(5,p.getHealth()-1)));p.getWorld().playSound(p.getLocation(),Sound.ENTITY_RAVAGER_ROAR,1.5f,0.8f);}
    private void frostPrison(Player p){if(!ready(p,"frost",25))return;for(LivingEntity t:targets(p.getLocation(),6,p)){t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,8));t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,100,128));t.setFreezeTicks(Math.min(t.getMaxFreezeTicks(),100));t.getWorld().spawnParticle(Particle.SNOWFLAKE,t.getLocation().add(0,1,0),50,0.8,1,0.8,0.04);}p.getWorld().playSound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,1.4f,0.6f);}
    private void summon(Player p){if(!ready(p,"summon",60))return;IronGolem g=p.getWorld().spawn(p.getLocation().add(p.getLocation().getDirection().multiply(2)),IronGolem.class);g.setCustomName(ChatColor.DARK_GRAY+"Страж пустоты");g.setCustomNameVisible(true);g.setPlayerCreated(true);g.setTarget(null);g.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,600,0));new BukkitRunnable(){int t=0;public void run(){if(!g.isValid()||t++>=600){if(g.isValid())g.remove();cancel();return;}if(t%20==0){LivingEntity nearest=null;double best=64;for(LivingEntity x:targets(g.getLocation(),8,p)){double d=x.getLocation().distanceSquared(g.getLocation());if(d<best){best=d;nearest=x;}}if(nearest!=null)g.setTarget(nearest);}}}.runTaskTimer(this,0,1);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_IRON_GOLEM_REPAIR,1.5f,0.6f);}

    @EventHandler public void onDamage(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player p))return;String id=id(p.getInventory().getItemInMainHand());if("blood_dagger".equals(id)){double heal=Math.min(3,e.getFinalDamage()*0.25);p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(),p.getHealth()+heal));p.getWorld().spawnParticle(Particle.HEART,e.getEntity().getLocation().add(0,1,0),5,0.3,0.3,0.3);}
        if("thunder_blade".equals(id)){if(ready(p,"chain",2)){LivingEntity hit=e.getEntity() instanceof LivingEntity l?l:null;if(hit!=null){p.getWorld().strikeLightningEffect(hit.getLocation());damage(hit,p,4);for(LivingEntity other:targets(hit.getLocation(),4,p))if(other!=hit){p.getWorld().strikeLightningEffect(other.getLocation());damage(other,p,3);}}}}
        if("void_scythe".equals(id)){int s=souls.getOrDefault(p.getUniqueId(),0);if(s>0){souls.put(p.getUniqueId(),s-1);e.setDamage(e.getDamage()+s*0.8);p.getWorld().spawnParticle(Particle.SOUL,e.getEntity().getLocation().add(0,1,0),25,0.5,0.5,0.5,0.05);}}
    }
    @EventHandler public void onKill(EntityDeathEvent e){Player p=e.getEntity().getKiller();if(p!=null && has(p,"void_scythe")){int value=Math.min(10,souls.getOrDefault(p.getUniqueId(),0)+1);souls.put(p.getUniqueId(),value);p.sendActionBar(ChatColor.DARK_PURPLE+"Души пустоты: "+ChatColor.LIGHT_PURPLE+value+"/10");p.getWorld().spawnParticle(Particle.SOUL,p.getLocation().add(0,1,0),18,0.4,0.6,0.4,0.05);}}
    @EventHandler public void onFatal(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p)||!has(p,"phoenix_feather")||p.getHealth()-e.getFinalDamage()>0)return;long now=System.currentTimeMillis();if(phoenixUsed.getOrDefault(p.getUniqueId(),0L)>now)return;phoenixUsed.put(p.getUniqueId(),now+3600000);e.setCancelled(true);p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(),10));p.setFireTicks(0);p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),100,1,1,1,0.5);p.getWorld().playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,2f,0.7f);}

    private org.bukkit.configuration.file.FileConfiguration config(){return getConfig();}
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){if(a.length==0){s.sendMessage(ChatColor.LIGHT_PURPLE+"/artifacts give <player> <item> [amount] | list | giveall <player> | reload");return true;}if(!s.hasPermission("artifacts.admin")){s.sendMessage(ChatColor.RED+"Нет прав.");return true;}switch(a[0].toLowerCase()){case "list"->{for(int i=0;i<IDS.length;i++)s.sendMessage(ChatColor.LIGHT_PURPLE+IDS[i]+ChatColor.GRAY+" — "+NAMES[i]);return true;}case "reload"->{reloadConfig();s.sendMessage(ChatColor.GREEN+"Конфигурация перезагружена.");return true;}case "giveall"->{if(a.length<2)return false;Player p=Bukkit.getPlayerExact(a[1]);if(p==null){s.sendMessage(ChatColor.RED+"Игрок не найден.");return true;}for(int i=0;i<IDS.length;i++)p.getInventory().addItem(makeItem(i));s.sendMessage(ChatColor.GREEN+"Выданы все 10 артефактов.");return true;}case "give"->{if(a.length<3)return false;Player p=Bukkit.getPlayerExact(a[1]);if(p==null){s.sendMessage(ChatColor.RED+"Игрок не найден.");return true;}int idx=Arrays.asList(IDS).indexOf(a[2].toLowerCase());if(idx<0){s.sendMessage(ChatColor.RED+"Неизвестный предмет. /artifacts list");return true;}int amount=1;try{if(a.length>3)amount=Math.max(1,Math.min(64,Integer.parseInt(a[3])));}catch(NumberFormatException ignored){}ItemStack item=makeItem(idx);item.setAmount(amount);p.getInventory().addItem(item);s.sendMessage(ChatColor.GREEN+"Выдано: "+NAMES[idx]);return true;}default->{s.sendMessage(ChatColor.RED+"Неизвестная подкоманда.");return true;}}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return List.of("give","giveall","list","reload").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();if(args.length==2&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("giveall")))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x->x.toLowerCase().startsWith(args[1].toLowerCase())).toList();if(args.length==3&&args[0].equalsIgnoreCase("give"))return Arrays.stream(IDS).filter(x->x.startsWith(args[2].toLowerCase())).toList();return List.of();}
}
