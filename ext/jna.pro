-dontwarn org.eclipse.swt.**
-dontwarn javafx.**
-dontwarn **.FXAbstractFeederGUI
-dontwarn **.FXResultTable$**
-dontwarn **.FXFileFeederGUI
-dontwarn **.FXRandomFeederGUI
-dontwarn **.FXRangeFeederGUI

-keepclassmembers class com.sun.jna.** {
    <fields>;
    <methods>;
}

-keepclassmembers class * extends com.sun.jna.** {
    <fields>;
    <methods>;
}
