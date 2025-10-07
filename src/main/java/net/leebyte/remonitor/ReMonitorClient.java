/*
 * ReMonitor - Remembers what monitor Minecraft was last open on.
 * Copyright (C) 2025 LeeByteCode

 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package net.leebyte.remonitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.Window;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReMonitorClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("remonitor.json");

    // Simple config
    private static class Config {
        long monitorIndex = -1;     // the monitor the window should move to
        boolean fullscreen = false; // if the window should be fullscreen
    }

    @Override
    public void onInitializeClient() {
        // When the client has fully started, move the window to the correct monitor.
        ClientLifecycleEvents.CLIENT_STARTED.register(ReMonitorClient::loadCurrentMonitor);

        // Before the client stops, remember current monitor and if the window was fullscreen.
        ClientLifecycleEvents.CLIENT_STOPPING.register(ReMonitorClient::saveCurrentMonitor);
    }

    /// Loads the saved config, moves the window to the monitor, and sets the window to fullscreen.
    private static void loadCurrentMonitor(MinecraftClient client) {
        // Load the saved config and exit if empty.
        Config config = load();
        if (config == null) return;

        // Get the window and find the monitor.
        Window window = client.getWindow();

        long targetMonitor = findMonitor(config);
        if (targetMonitor == 0L) return;

        // If already fullscreen, toggle to windowed so we can move to the right monitor,
        // then re-toggle fullscreen if requested.
        boolean wasFullscreenAtStart = window.isFullscreen();
        if (wasFullscreenAtStart) {
            window.toggleFullscreen();
        }

        // Center the window on the target monitor.
        centerWindowOnMonitor(targetMonitor, window.getHandle());

        // Re-enter fullscreen if indicated in the config.
        if (config.fullscreen && !window.isFullscreen()) {
            window.toggleFullscreen();
        }
    }

    private static void saveCurrentMonitor(MinecraftClient client) {
        try {
            // Get the window and create the config
            Window window = client.getWindow();
            Config config = new Config();
            config.fullscreen = window.isFullscreen();

            // Get the monitor handle
            Monitor mon = window.getMonitor();
            long monitorPtr = 0L;
            if (mon != null) {
                monitorPtr = mon.getHandle();
            }

            // Save the monitor index
            config.monitorIndex = indexOfMonitor(monitorPtr);

            // Save the config as a file
            try (BufferedWriter w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, w);
            }
        } catch (Exception ignored) {
            // Don't save if there is an error.
        }
    }

    /// Loads the config file.
    private static Config load() {
        if (!Files.isRegularFile(CONFIG_PATH)) return null;

        // Read the config file
        try (BufferedReader r = Files.newBufferedReader(CONFIG_PATH)) {
            return GSON.fromJson(r, Config.class);
        } catch (IOException e) {
            // The file could not be parsed as JSON.
            return null;
        }
    }

    /// Finds the monitor handle using the saved index.
    private static long findMonitor(Config config) {
        // Get the list of available monitors
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors == null || monitors.limit() == 0) return 0L;

        // Find exact index match
        if (config.monitorIndex >= 0 && config.monitorIndex < monitors.limit()) {
            long m = monitors.get((int) config.monitorIndex);
            if (m != 0L) return m;
        }

        // Use primary monitor as a fallback
        long primary = GLFW.glfwGetPrimaryMonitor();
        if (primary != 0L) return primary;

        // Use first in list as a last resort
        return monitors.get(0);
    }

    /// Returns the index of the given monitor handle or -1 if not found.
    private static long indexOfMonitor(long monitorPtr) {
        // Empty monitor handle
        if (monitorPtr == 0L) return -1;

        PointerBuffer monitors = GLFW.glfwGetMonitors();
        // No monitors
        if (monitors == null) return -1;

        // Find matching monitor
        for (int i = 0; i < monitors.limit(); i++) {
            if (monitors.get(i) == monitorPtr) return i;
        }

        // No match
        return -1;
    }

    /// Centers the given window on the specified monitor using screen coordinates.
    private static void centerWindowOnMonitor(long monitor, long windowHandle) {
        if (monitor == 0L || windowHandle == 0L) return;

        // Get monitor position and size (screen coordinates)
        int[] mx = new int[1];
        int[] my = new int[1];
        GLFW.glfwGetMonitorPos(monitor, mx, my);

        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) return;

        int monitorW = mode.width();
        int monitorH = mode.height();

        // Current window size (screen coordinates)
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowSize(windowHandle, ww, wh);

        // Get centered position on the monitor
        int nx = mx[0] + Math.max(0, (monitorW - ww[0]) / 2);
        int ny = my[0] + Math.max(0, (monitorH - wh[0]) / 2);

        GLFW.glfwSetWindowPos(windowHandle, nx, ny);
    }
}