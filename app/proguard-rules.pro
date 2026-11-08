

-keepattributes SourceFile,LineNumberTable

-dontobfuscate

# 保留 Matroska 章节发布和 WebM 描述字幕适配需要访问的字段。
-keepclassmembers class androidx.media3.extractor.mkv.MatroskaExtractor {
    private androidx.media3.extractor.mkv.EbmlReader reader;
    private android.util.SparseArray tracks;
}
-keep class androidx.media3.extractor.mkv.DefaultEbmlReader {
    private androidx.media3.extractor.mkv.EbmlProcessor processor;
}
-keep class androidx.media3.extractor.mkv.MatroskaExtractor$Track {
    androidx.media3.common.Format format;
    public androidx.media3.extractor.TrackOutput output;
    boolean waitingForDtsAnalysis;
}

-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# Match Next Player: smbj-rpc references this desktop-only exception type.
-dontwarn java.rmi.UnmarshalException

# SMBJ ProGuard Rules
# Keep SMBJ classes
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msdtyp.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-keep class com.hierynomus.spnego.** { *; }
-keep class com.hierynomus.ntlm.** { *; }
-keep class com.hierynomus.security.** { *; }

# mbassador creates the default handler invocation through Class.getConstructor().
-keepclassmembers,allowobfuscation class net.engio.mbassy.dispatch.ReflectiveHandlerInvocation {
    public <init>(net.engio.mbassy.subscription.SubscriptionContext);
}
