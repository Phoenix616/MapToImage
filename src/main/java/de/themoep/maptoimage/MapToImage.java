package de.themoep.maptoimage;

/*
 * MapToImage
 * Copyright (c) 2018 Max Lee aka Phoenix616 (mail@moep.tv)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Level;

public final class MapToImage extends JavaPlugin {

    private static final String MAP_PREFIX = "map";
    private File imageFolder;
    private Field fieldWorldMap;
    private Field colorField;

    @Override
    public void onEnable() {
        getCommand("maptoimage").setExecutor(this);
        imageFolder = new File(getDataFolder(), "images");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
        loadReflections(getServer().getMap((short) 0));
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            if ("all".equalsIgnoreCase(args[0])) {
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    sender.sendMessage(ChatColor.YELLOW + "Started generating images for all maps...");
                    int generated = generateAll();
                    sender.sendMessage(ChatColor.YELLOW + "" + generated + " map images generated!");
                });
                return true;
            } else if (args.length == 1) {
                try {
                    short id = getMapId(args[0]);
                    getServer().getScheduler().runTaskAsynchronously(this, () -> {
                        if (generate(id)) {
                            sender.sendMessage(ChatColor.YELLOW + "Generated image for map " + id + " generated!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Could not generate image for map " + id + "!");
                        }
                    });
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + args[0] + " is not a valid map id!");
                }
                return true;
            } else if (args.length == 2) {
                String tried = args[0];
                try {
                    short startId = getMapId(args[0]);
                    tried = args[1];
                    short endId = getMapId(args[1]);
                    getServer().getScheduler().runTaskAsynchronously(this, () -> {
                        sender.sendMessage(ChatColor.YELLOW + "Started generating images for maps between ID " + startId + " and " + endId + "...");
                        int generated = generate(startId, endId);
                        sender.sendMessage(ChatColor.YELLOW + "" + generated + " images for maps between ID " + startId + " and " + endId + " generated!");
                    });
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + tried + " is not a valid map id!");
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private short getMapId(String string) throws IllegalArgumentException {
        if (string.startsWith(MAP_PREFIX)) {
            string = string.substring(MAP_PREFIX.length());
        }
        short i = Short.parseShort(string);
        if (i < 0) {
            i = Short.MAX_VALUE;
        }
        return i;
    }

    private boolean loadReflections(MapView map) {
        if (map != null) {
            try {
                if (fieldWorldMap == null || !fieldWorldMap.isAccessible()) {
                    fieldWorldMap = map.getClass().getDeclaredField("worldMap");
                    fieldWorldMap.setAccessible(true);
                }
                if (colorField == null) {
                    Object worldMap = fieldWorldMap.get(map);
                    colorField = worldMap.getClass().getField("colors");
                }
                return true;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                getLogger().log(Level.SEVERE, "Could not load field required for map color reading!", e);
            }
        }
        return false;
    }

    private boolean saveImage(MapView map) {
        if (loadReflections(map)) {
            try {
                Object worldMap = fieldWorldMap.get(map);
                byte[] colors = (byte[]) colorField.get(worldMap);

                BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

                for (int x = 0; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        int i = x + y * 128;
                        if (i < colors.length) {
                            byte color = colors[i];
                            try {
                                image.setRGB(x, y, MapPalette.getColor(color).getRGB());
                            } catch (IndexOutOfBoundsException e) {
                                getLogger().log(Level.SEVERE, "Could not get color widh ID " + color + "! ");
                            }
                        }
                    }
                }
                File imageFile = new File(imageFolder, "map" + map.getId() + ".png");
                ImageIO.write(image, "png", imageFile);
                return true;
            } catch (IllegalAccessException e) {
                getLogger().log(Level.SEVERE, "Could not access field in WorldMap class for " +  map.getId() + "! ", e);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not write map with id " +  map.getId() + " to image file! ", e);
            }
        }
        return false;
    }

    private boolean generate(short id) {
        MapView map = getServer().getMap(id);
        return map != null && saveImage(map);
    }

    private int generate(short startId, short endId) {
        if (startId > endId) {
            short tempEndId = endId;
            endId = startId;
            startId = tempEndId;
        }
        int generated = 0;
        for (short i = startId; i <= endId; i++) {
            MapView map = getServer().getMap(i);
            if (map == null) {
                getLogger().log(Level.INFO, "Only found maps until ID " + (i - 1) + "!");
                break;
            }
            if (saveImage(map)) {
                generated++;
                if (generated % 500 == 0) {
                    getLogger().log(Level.INFO, generated + " Maps generated...");
                }
            }
        }
        return generated;
    }

    private int generateAll() {
        int generated = 0;
        for (short i = 0; i < Short.MAX_VALUE; i++) {
            MapView map = getServer().getMap(i);
            if (map == null) {
                getLogger().log(Level.INFO, i + " Maps found!");
                break;
            }
            if (saveImage(map)) {
                generated++;
                if (generated % 500 == 0) {
                    getLogger().log(Level.INFO, generated + " Maps generated...");
                }
            }
        }
        return generated;
    }
}
