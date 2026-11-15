package org.taumc.celeritas.patch;

/**
 * Minimal runtime replacement for the object Mixin-generated injections pass to
 * their handlers.  The patched client has no Mixin or ASM on its class path, so
 * only the callback state that the generated code actually uses is kept.
 */
public class CallbackInfo {
    private final String name;
    private final boolean cancellable;
    private boolean cancelled;

    public CallbackInfo(String name, boolean cancellable) {
        this.name = name;
        this.cancellable = cancellable;
    }

    public String getId() {
        return name;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        if (!cancellable) {
            throw new IllegalStateException("Callback is not cancellable");
        }
        cancelled = true;
    }

    @Override
    public String toString() {
        return "CallbackInfo[" + name + "]";
    }
}
