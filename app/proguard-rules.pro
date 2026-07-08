# Add project specific ProGuard rules here.
-keep class com.techducat.macrotrack.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# --- I2P Router keep rules (mirrors buzzr-p2p/verzus-p2p) -------------------
# All net.i2p classes are accessed via reflection in EmbeddedI2PRouter.
# Without these rules R8 renames/strips constructors and causes
# NoSuchMethodException at runtime (symptom: net.i2p.router.a.<init> []).

# Preserve all I2P classes and members
-keep class net.i2p.** { *; }
-dontwarn net.i2p.**

# Router constructors tried in createRouter() fallback chain
-keepclassmembers class net.i2p.router.Router {
    public <init>(java.lang.String, java.util.Properties);
    public <init>(java.lang.String);
    public <init>();
    public void runRouter();
    public boolean isAlive();
    public boolean isRunning();
    public void setKillVMOnEnd(boolean);
    public void shutdown(int);
}

# SAMBridge constructors tried in startSAMBridge() fallback chain
-keepclassmembers class net.i2p.sam.SAMBridge {
    public <init>(java.lang.String, int, boolean, java.util.Properties, java.lang.String, java.io.File, net.i2p.sam.SAMSecureSessionInterface);
    public <init>(java.lang.String, int, java.util.Properties);
    public <init>();
    public void startListening();
}

# RouterContext -- constructed internally by Router, must keep its name
-keep class net.i2p.router.RouterContext { *; }

# SAMSecureSessionInterface -- referenced in SAMBridge 7-arg constructor
-keep interface net.i2p.sam.SAMSecureSessionInterface { *; }
# -----------------------------------------------------------------------------
