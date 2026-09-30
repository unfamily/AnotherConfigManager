package net.unfamily.anotherconfigmanager.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dirty edit buffer for one {@link ModConfigSpec}: snapshot on open, commit/discard/undo later.
 */
public final class ConfigEditSession {
    private static final int MAX_UNDO = 64;

    private final ModConfigSpec spec;
    private final Map<String, ModConfigSpec.ConfigValue<?>> valuesByPath = new LinkedHashMap<>();
    private final Map<String, Object> originals = new HashMap<>();
    private final Map<String, Object> pending = new HashMap<>();
    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();

    public ConfigEditSession(ModConfigSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        collectValues(spec.getValues(), List.of());
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : valuesByPath.entrySet()) {
            originals.put(entry.getKey(), snapshot(safeGet(entry.getValue())));
        }
    }

    public ModConfigSpec spec() {
        return spec;
    }

    public boolean isDirty() {
        for (Map.Entry<String, Object> entry : pending.entrySet()) {
            Object original = originals.get(entry.getKey());
            if (!Objects.equals(normalize(original), normalize(entry.getValue()))) {
                return true;
            }
        }
        return false;
    }

    public boolean isDirty(List<String> path) {
        String key = pathKey(path);
        if (!pending.containsKey(key)) {
            return false;
        }
        return !Objects.equals(normalize(originals.get(key)), normalize(pending.get(key)));
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object getEffective(ModConfigSpec.ConfigValue<?> value) {
        String key = pathKey(value.getPath());
        if (pending.containsKey(key)) {
            return pending.get(key);
        }
        return safeGet(value);
    }

    public Object getEffective(List<String> path) {
        ModConfigSpec.ConfigValue<?> value = valuesByPath.get(pathKey(path));
        return value == null ? null : getEffective(value);
    }

    public Object getOriginal(List<String> path) {
        return originals.get(pathKey(path));
    }

    public Object getDefault(List<String> path) {
        ModConfigSpec.ConfigValue<?> value = valuesByPath.get(pathKey(path));
        return value == null ? null : value.getDefault();
    }

    @Nullable
    public ModConfigSpec.ConfigValue<?> valueAt(List<String> path) {
        return valuesByPath.get(pathKey(path));
    }

    public void setPending(List<String> path, Object newValue) {
        String key = pathKey(path);
        ModConfigSpec.ConfigValue<?> value = valuesByPath.get(key);
        if (value == null) {
            return;
        }
        Object before = getEffective(value);
        Object snap = snapshot(newValue);
        if (Objects.equals(normalize(before), normalize(snap))) {
            return;
        }
        pushUndo(key, before);
        Object original = originals.get(key);
        if (Objects.equals(normalize(original), normalize(snap))) {
            pending.remove(key);
        } else {
            pending.put(key, snap);
        }
    }

    public void resetValue(List<String> path) {
        ModConfigSpec.ConfigValue<?> value = valuesByPath.get(pathKey(path));
        if (value == null) {
            return;
        }
        setPending(path, snapshot(value.getDefault()));
    }

    /** Revert this path to the value from when the session opened (clears pending). */
    public boolean revertValue(List<String> path) {
        String key = pathKey(path);
        if (!pending.containsKey(key)) {
            return false;
        }
        pending.remove(key);
        return true;
    }

    public boolean differsFromDefault(List<String> path) {
        ModConfigSpec.ConfigValue<?> value = valuesByPath.get(pathKey(path));
        if (value == null) {
            return false;
        }
        return !Objects.equals(normalize(getEffective(value)), normalize(value.getDefault()));
    }

    public void resetAll() {
        List<String> keys = new ArrayList<>(valuesByPath.keySet());
        for (String key : keys) {
            ModConfigSpec.ConfigValue<?> value = valuesByPath.get(key);
            if (value == null) {
                continue;
            }
            Object def = snapshot(value.getDefault());
            Object before = getEffective(value);
            if (Objects.equals(normalize(before), normalize(def))) {
                continue;
            }
            pushUndo(key, before);
            if (Objects.equals(normalize(originals.get(key)), normalize(def))) {
                pending.remove(key);
            } else {
                pending.put(key, def);
            }
        }
    }

    public boolean undo() {
        UndoEntry entry = undoStack.pollLast();
        if (entry == null) {
            return false;
        }
        Object original = originals.get(entry.pathKey());
        if (Objects.equals(normalize(original), normalize(entry.previousValue()))) {
            pending.remove(entry.pathKey());
        } else {
            pending.put(entry.pathKey(), snapshot(entry.previousValue()));
        }
        return true;
    }

    /**
     * Writes only dirty pending values to the live {@link ModConfigSpec}, then saves.
     * Untouched keys are never rewritten (avoids resetting B/C when only A changed).
     * <p>
     * Matches Configured's save path for hot-reloadable values ({@code RestartType.NONE}):
     * {@code set} → {@link ModConfigSpec#afterReload()} → {@link ModConfigSpec#save()}
     * which persists to disk and fires {@code ModConfigEvent.Reloading}. Values marked
     * world/game restart keep their cached value until the required restart.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void commit() {
        if (!isDirty()) {
            return;
        }
        if (!spec.isLoaded()) {
            AnotherConfigManager.LOGGER.warn("Cannot commit config edits: ModConfigSpec is not loaded");
            return;
        }
        for (Map.Entry<String, Object> entry : pending.entrySet()) {
            Object original = originals.get(entry.getKey());
            if (Objects.equals(normalize(original), normalize(entry.getValue()))) {
                continue;
            }
            ModConfigSpec.ConfigValue value = valuesByPath.get(entry.getKey());
            if (value == null) {
                continue;
            }
            value.set(entry.getValue());
            originals.put(entry.getKey(), snapshot(entry.getValue()));
        }
        pending.clear();
        undoStack.clear();
        // Clear RestartType.NONE caches so get() re-reads (Configured resetConfigCache parity).
        spec.afterReload();
        // Disk write + ModConfigEvent.Reloading for listeners.
        spec.save();
    }

    public void discard() {
        pending.clear();
        undoStack.clear();
    }

    private void pushUndo(String pathKey, Object previous) {
        undoStack.addLast(new UndoEntry(pathKey, snapshot(previous)));
        while (undoStack.size() > MAX_UNDO) {
            undoStack.pollFirst();
        }
    }

    private void collectValues(UnmodifiableConfig config, List<String> path) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            Object raw = entry.getValue();
            List<String> childPath = append(path, entry.getKey());
            if (raw instanceof ModConfigSpec.ConfigValue<?> configValue) {
                valuesByPath.put(pathKey(childPath), configValue);
            } else if (raw instanceof UnmodifiableConfig nested) {
                collectValues(nested, childPath);
            }
        }
    }

    private static Object safeGet(ModConfigSpec.ConfigValue<?> value) {
        try {
            return value.get();
        } catch (RuntimeException error) {
            return value.getDefault();
        }
    }

    private static Object snapshot(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }

    private static Object normalize(Object value) {
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        return value;
    }

    public static String pathKey(List<String> path) {
        return String.join(".", path);
    }

    private static List<String> append(List<String> path, String key) {
        List<String> next = new ArrayList<>(path.size() + 1);
        next.addAll(path);
        next.add(key);
        return List.copyOf(next);
    }

    private record UndoEntry(String pathKey, Object previousValue) {}
}
