// 工程级构建脚本
// AGP 7.4.2 对 compileSdk 28 兼容性最好（无最低 compileSdk 限制），需要 JDK 11。
plugins {
    id("com.android.application") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.22" apply false
}
