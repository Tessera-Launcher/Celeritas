package org.taumc.celeritas.patcher;

import java.util.logging.Logger;

import org.spongepowered.asm.service.IMixinAuditTrail;

/** Audit output is useful when a mixin silently refuses to apply. */
final class PatcherAuditTrail implements IMixinAuditTrail {
    private static final Logger LOG = Logger.getLogger("celeritas-patcher");

    @Override
    public void onApply(String className, String mixinName) {
        LOG.fine(() -> "Mixin " + mixinName + " -> " + className);
    }

    @Override
    public void onPostProcess(String className) {
        LOG.finer(() -> "Post-processed " + className);
    }

    @Override
    public void onGenerate(String className, String generatorName) {
        LOG.fine(() -> "Generated " + className + " via " + generatorName);
    }
}
