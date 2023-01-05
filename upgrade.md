# v2.0.0
- 将`ClassProvider`升级为`ResourceProvider`，支持从嵌套jar中搜索所有类型的资源而不只是class，以此支持ServiceLoader等；


# v3.0.0
- 整体重构，参考使用spring中的部分代码实现嵌套jar的加载；