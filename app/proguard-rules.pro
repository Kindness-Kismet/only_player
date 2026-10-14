

-keepattributes SourceFile,LineNumberTable

-dontobfuscate

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
