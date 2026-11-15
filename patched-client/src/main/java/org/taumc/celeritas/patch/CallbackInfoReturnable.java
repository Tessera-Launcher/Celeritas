package org.taumc.celeritas.patch;

/**
 * Minimal runtime replacement for Mixin's return-value callback.  See
 * {@link CallbackInfo}.
 */
@SuppressWarnings("unchecked")
public class CallbackInfoReturnable<R> extends CallbackInfo {
    private R returnValue;

    public CallbackInfoReturnable(String name, boolean cancellable) {
        super(name, cancellable);
    }

    public CallbackInfoReturnable(String name, boolean cancellable, R returnValue) {
        super(name, cancellable);
        this.returnValue = returnValue;
    }

    public CallbackInfoReturnable(String name, boolean cancellable, byte value) { super(name, cancellable); this.returnValue = (R) Byte.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, char value) { super(name, cancellable); this.returnValue = (R) Character.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, double value) { super(name, cancellable); this.returnValue = (R) Double.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, float value) { super(name, cancellable); this.returnValue = (R) Float.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, int value) { super(name, cancellable); this.returnValue = (R) Integer.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, long value) { super(name, cancellable); this.returnValue = (R) Long.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, short value) { super(name, cancellable); this.returnValue = (R) Short.valueOf(value); }
    public CallbackInfoReturnable(String name, boolean cancellable, boolean value) { super(name, cancellable); this.returnValue = (R) Boolean.valueOf(value); }

    public R getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(R value) {
        this.returnValue = value;
    }

    public byte getReturnValueB() { return ((Number) returnValue).byteValue(); }
    public char getReturnValueC() { return (Character) returnValue; }
    public double getReturnValueD() { return ((Number) returnValue).doubleValue(); }
    public float getReturnValueF() { return ((Number) returnValue).floatValue(); }
    public int getReturnValueI() { return ((Number) returnValue).intValue(); }
    public long getReturnValueJ() { return ((Number) returnValue).longValue(); }
    public short getReturnValueS() { return ((Number) returnValue).shortValue(); }
    public boolean getReturnValueZ() { return (Boolean) returnValue; }

    public void setReturnValueB(byte value) { this.returnValue = (R) Byte.valueOf(value); }
    public void setReturnValueC(char value) { this.returnValue = (R) Character.valueOf(value); }
    public void setReturnValueD(double value) { this.returnValue = (R) Double.valueOf(value); }
    public void setReturnValueF(float value) { this.returnValue = (R) Float.valueOf(value); }
    public void setReturnValueI(int value) { this.returnValue = (R) Integer.valueOf(value); }
    public void setReturnValueJ(long value) { this.returnValue = (R) Long.valueOf(value); }
    public void setReturnValueS(short value) { this.returnValue = (R) Short.valueOf(value); }
    public void setReturnValueZ(boolean value) { this.returnValue = (R) Boolean.valueOf(value); }
}
