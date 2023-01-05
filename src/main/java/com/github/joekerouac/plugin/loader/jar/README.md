该包中的代码实现了jar协议的解析（`com.github.joekerouac.plugin.loader.jar.Handler`），替代了系统自带的jar协议解析器（`sun.net.www.protocol.jar.Handler`），支持使用嵌套jar中的jar，为此我们又重写了`java.util.jar.JarFile`、`java.util.jar.JarEntry`、`java.net.JarURLConnection`;


URL解析核心部分就是选择协议对应的`java.net.URLStreamHandler`来处理，这部分代码在`java.net.URL.getURLStreamHandler`中;