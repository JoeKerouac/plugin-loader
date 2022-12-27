# 插件加载器
## 说明
用于提供插件加载的ClassLoader

## 开发必看
注意，本包中请勿添加任何其他依赖，否则可能会导致一些错误；

## 相关算法支撑
本加载器使用到了zip文件及其解析，相关理论文章参考：https://mp.weixin.qq.com/s?__biz=MzIxNDE0MDQwMA==&mid=2247483945&idx=1&sn=2e90b0ae7e38f0c1c4d1ebded62dda50&chksm=97ad56b6a0dadfa014c8e65b34a709a15fa139facc8063b39d5f658d95a780f580ed30b53af1&token=1915080617&lang=zh_CN#rd


## 嵌套jar打包


使用打包插件打包：
```
<project>
    <build>
        <plugins>
            <plugin>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.0.0</version>
                <configuration>
                    <descriptors>
                        <!-- 打包描述文件 -->
                        <descriptor>fat.xml</descriptor>
                    </descriptors>
                    <!-- 这个很关键，打包的jar包只是存储，不压缩 -->
                    <archiverConfig>
                        <compress>false</compress>
                    </archiverConfig>
                </configuration>
                <executions>
                    <execution>
                        <id>make-assembly</id>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>

```

项目根目录放入`fat.xml`，内容如下：

> 插件描述xml详细说明文档：https://maven.apache.org/plugins/maven-assembly-plugin/assembly.html

```
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.1.1 https://maven.apache.org/xsd/assembly-2.1.1.xsd">
    <id>fat</id>
    <!-- 默认是true，应该修改为false，否则打出来的jar包中还会有一个根目录，根目录中存放的才是class和lib -->
    <includeBaseDirectory>false</includeBaseDirectory>
    <!-- 打包格式 -->
    <formats>
        <format>jar</format>
    </formats>


    <!-- 依赖 -->
    <dependencySets>
        <!-- 将除了plugin-loader外的其他依赖直接放到jar中的lib目录中，不解压缩 -->
        <dependencySet>
            <outputDirectory>/lib</outputDirectory>
            <excludes>
                <exclude>com.github.JoeKerouac:plugin-loader</exclude>
            </excludes>
            <!-- 是否将依赖解压，true表示解压 -->
            <unpack>false</unpack>
        </dependencySet>

        <!-- 将项目class和plugin-loader解压缩放入jar包根目录，这样普通类加载器就能加载到 -->
        <dependencySet>
            <outputDirectory>/</outputDirectory>
            <includes>
                <include>${project.groupId}:${project.artifactId}</include>
                <include>com.github.JoeKerouac:plugin-loader</include>
            </includes>
            <!-- 是否将依赖解压，true表示解压 -->
            <unpack>true</unpack>
        </dependencySet>
    </dependencySets>
</assembly>
```

