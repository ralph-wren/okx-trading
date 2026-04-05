package com.okx.trading.service.impl;

import com.okx.trading.model.entity.StrategyInfoEntity;
import com.okx.trading.service.StrategyInfoService;
import com.okx.trading.strategy.StrategyFactory1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 基于Java Compiler API的动态策略服务
 * 相比Janino具有更好的错误信息和完整的Java语法支持
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaCompilerDynamicStrategyService {

    private final StrategyInfoService strategyInfoService;

    // 缓存已编译的策略函数
    private final Map<String, Function<BarSeries, Strategy>> compiledStrategies = new ConcurrentHashMap<>();

    // 临时编译目录
    private final Path tempCompileDir = Paths.get(System.getProperty("java.io.tmpdir"), "okx-trading-compiled-strategies");

    /**
     * Spring Boot 3.2+ fat jar 内 BOOT-INF/lib 下嵌套 jar 解压缓存，供 javac 使用（javac 无法直接读 nested: / 部分 jar: 嵌套路径）
     */
    private final Path nestedLibCacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "okx-trading-nested-libs-cache");

    // Java编译器
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    /**
     * 编译策略代码并加载到StrategyFactory
     */
    public Function<BarSeries, Strategy> compileAndLoadStrategy(
            String strategyCode, StrategyInfoEntity strategyEntity) {
        try {
            // 检查编译器可用性
            if (compiler == null) {
                throw new RuntimeException("Java Compiler API不可用，请确保运行在JDK而非JRE环境中");
            }

            // 编译策略代码
            Function<BarSeries, Strategy> strategyFunction = compileStrategyCode(strategyCode, strategyEntity.getStrategyCode());

            // 缓存策略函数
            compiledStrategies.put(strategyEntity.getStrategyCode(), strategyFunction);

            // 动态加载到StrategyFactory
            loadStrategyToFactory(strategyEntity.getStrategyCode(), strategyFunction);

            log.info("策略 {} 使用Java Compiler API编译并加载成功", strategyEntity.getStrategyCode());
            return strategyFunction;
        } catch (Exception e) {
            log.error("使用Java Compiler API编译策略代码失败: {}, 编译的代码: {}", e.getMessage(), strategyCode);
            throw new RuntimeException("编译策略代码失败: " + e.getMessage());
        }
    }

    /**
     * 编译策略代码
     */
    private Function<BarSeries, Strategy> compileStrategyCode(String strategyCode, String strategyId) throws Exception {
        // 确保临时目录存在
        if (!Files.exists(tempCompileDir)) {
            Files.createDirectories(tempCompileDir);
        }

        // 从代码中提取类名和方法名
        String className = extractClassName(strategyCode);
        String methodName = extractMethodName(strategyCode);

        // 准备完整的源代码
        String fullSourceCode = prepareFullSourceCode(strategyCode);

        // 创建源文件
        Path sourceFile = tempCompileDir.resolve(className + ".java");
        Files.write(sourceFile, fullSourceCode.getBytes("UTF-8"));

        // 准备编译选项 - 禁用注解处理器以避免Lombok冲突
        List<String> options = Arrays.asList(
            "-classpath", buildClasspath(),
            "-d", tempCompileDir.toString(),
            "-proc:none"  // 禁用注解处理器
        );

        // 获取文件管理器
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        // 获取编译单元
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile());

        // 创建编译任务
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, diagnostics, options, null, compilationUnits);

        // 执行编译
        boolean success = task.call();

        if (!success) {
            StringBuilder errorMessage = new StringBuilder("编译失败:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorMessage.append(String.format("Line %d, Column %d: %s\n",(Object) diagnostic.getLineNumber(), (Object) diagnostic.getColumnNumber(), diagnostic.getMessage(null)));
            }
            throw new RuntimeException(errorMessage.toString());
        }

        // 加载编译后的类
        URL[] urls = {tempCompileDir.toUri().toURL()};
        URLClassLoader classLoader = new URLClassLoader(urls, this.getClass().getClassLoader());
        Class<?> strategyClass = classLoader.loadClass(className);

        // 创建策略函数 - 调用静态方法而不是构造函数
        return (series) -> {
            try {
                // 查找静态方法
                var method = strategyClass.getMethod(methodName, BarSeries.class);
                return (Strategy) method.invoke(null, series);
            } catch (Exception e) {
                throw new RuntimeException("调用策略静态方法失败: " + e.getMessage(), e);
            }
        };
    }

    /**
     * 准备完整的源代码，包含所有必要的import
     */
    private String prepareFullSourceCode(String strategyCode) {
        StringBuilder fullCode = new StringBuilder();

        // 添加必要的import语句（Ta4j 0.18：SMA/EMA 等在 indicators.averages 子包，通配符 indicators.* 无法覆盖子包）
        fullCode.append("import org.ta4j.core.*;\n");
        fullCode.append("import org.ta4j.core.indicators.*;\n");
        fullCode.append("import org.ta4j.core.indicators.averages.*;\n");
        fullCode.append("import org.ta4j.core.indicators.helpers.*;\n");
        fullCode.append("import org.ta4j.core.indicators.bollinger.*;\n");
        fullCode.append("import org.ta4j.core.indicators.statistics.*;\n");
        fullCode.append("import org.ta4j.core.indicators.volume.*;\n");
        fullCode.append("import org.ta4j.core.rules.*;\n");
        fullCode.append("import org.ta4j.core.num.*;\n");
        fullCode.append("import java.util.*;\n");
        fullCode.append("import java.math.*;\n");
        fullCode.append("\n");

        // 添加策略代码
        fullCode.append(strategyCode);

        return fullCode.toString();
    }

    /**
     * 构建 javac 可用的完整 classpath：包含 {@code java.class.path} 以及当前线程 / 本类 {@link URLClassLoader} 链上的所有 URL。
     * <p>
     * Spring Boot 可执行 jar 运行时依赖多在 {@link URLClassLoader#getURLs()} 中（含 {@code nested:} 协议），
     * 仅靠 {@code java.class.path} 往往只有外层 jar，会导致动态编译找不到 Ta4j 等依赖。
     * 对 {@code nested:} 及 jar 内嵌套 lib 条目会解压到临时目录后再加入 classpath。
     */
    private String buildClasspath() {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        String jcp = System.getProperty("java.class.path", "");
        if (!jcp.isBlank()) {
            for (String part : jcp.split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    entries.add(Paths.get(part).toAbsolutePath().normalize().toString());
                }
            }
        }
        addClasspathEntriesFromLoader(entries, Thread.currentThread().getContextClassLoader());
        addClasspathEntriesFromLoader(entries, JavaCompilerDynamicStrategyService.class.getClassLoader());
        String joined = String.join(File.pathSeparator, entries);
        if (log.isDebugEnabled()) {
            log.debug("动态策略编译 classpath 共 {} 个条目", entries.size());
        }
        return joined;
    }

    /** 沿父链收集 {@link URLClassLoader} 的 URL，转为 javac 可识别的本地路径或解压后的 jar 路径 */
    private void addClasspathEntriesFromLoader(Set<String> entries, ClassLoader loader) {
        for (ClassLoader cl = loader; cl != null; cl = cl.getParent()) {
            if (!(cl instanceof URLClassLoader urlCl)) {
                continue;
            }
            for (URL url : urlCl.getURLs()) {
                String entry = urlToClasspathEntry(url);
                if (entry != null && !entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
    }

    /**
     * 将 ClassLoader 的 URL 转为 javac {@code -classpath} 片段。
     */
    private String urlToClasspathEntry(URL url) {
        if (url == null) {
            return null;
        }
        try {
            String protocol = url.getProtocol();
            if ("file".equalsIgnoreCase(protocol)) {
                return Paths.get(url.toURI()).toAbsolutePath().normalize().toString();
            }
            if ("nested".equalsIgnoreCase(protocol)) {
                return nestedUrlToExtractedJarPath(url);
            }
            if ("jar".equalsIgnoreCase(protocol)) {
                return jarUrlToClasspathEntry(url);
            }
        } catch (Exception e) {
            log.warn("无法将 URL 加入动态编译 classpath，已跳过: {}", url, e);
        }
        return null;
    }

    /**
     * 解析 Spring Boot 3.2+ {@code nested:/path/app.jar/!BOOT-INF/lib/foo.jar}，解压内层 jar 到缓存目录。
     *
     * @see <a href="https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-tools/spring-boot-loader/src/main/java/org/springframework/boot/loader/net/protocol/nested/NestedLocation.java">NestedLocation</a>
     */
    private String nestedUrlToExtractedJarPath(URL nestedUrl) throws IOException {
        String decoded = URLDecoder.decode(nestedUrl.toString().substring("nested:".length()), StandardCharsets.UTF_8);
        int sep = decoded.lastIndexOf("/!");
        if (sep < 0) {
            return null;
        }
        String outerPathStr = decoded.substring(0, sep);
        // Windows：nested:/C:/app.jar 形式下路径可能以 / 开头，需与 Spring NestedLocation 一致去掉前导 /
        if (File.separatorChar == '\\' && outerPathStr.startsWith("/") && outerPathStr.length() > 2
                && outerPathStr.charAt(2) == ':') {
            outerPathStr = outerPathStr.substring(1);
        }
        Path outerJar = Paths.get(outerPathStr);
        String zipEntryPath = decoded.substring(sep + 2);
        if (zipEntryPath.endsWith("/")) {
            zipEntryPath = zipEntryPath.substring(0, zipEntryPath.length() - 1);
        }
        if (!zipEntryPath.endsWith(".jar")) {
            // 目录类条目（如 BOOT-INF/classes）由其他 URL 覆盖，javac 不需要单独嵌套目录
            return null;
        }
        return extractZipEntryToCache(outerJar, zipEntryPath);
    }

    /**
     * 处理 {@code jar:} URL：若指向整包则返回文件路径；若带 {@code !/} 内层 jar 条目则解压后返回路径。
     */
    private String jarUrlToClasspathEntry(URL jarUrl) throws IOException {
        try {
            URLConnection conn = jarUrl.openConnection();
            if (conn instanceof JarURLConnection jarConn) {
                URL jarFileUrl = jarConn.getJarFileURL();
                Path outerPath = Paths.get(jarFileUrl.toURI()).toAbsolutePath().normalize();
                String entryName = jarConn.getEntryName();
                if (entryName != null && entryName.endsWith(".jar")) {
                    return extractZipEntryToCache(outerPath, entryName);
                }
                return outerPath.toString();
            }
            // 非标准 JarURLConnection，尝试按字符串解析 jar:file:/path/!entry
            String external = jarUrl.toExternalForm();
            int schemeSep = external.indexOf(':');
            if (schemeSep < 0) {
                return null;
            }
            String rest = external.substring(schemeSep + 1);
            if (!rest.startsWith("file:")) {
                return null;
            }
            int bang = rest.indexOf("!/");
            if (bang < 0) {
                URL filePart = new URL(rest);
                return Paths.get(filePart.toURI()).toAbsolutePath().normalize().toString();
            }
            String fileUrlPart = rest.substring(0, bang);
            String entry = rest.substring(bang + 2);
            if (entry.endsWith("/")) {
                entry = entry.substring(0, entry.length() - 1);
            }
            Path outer = Paths.get(new URL(fileUrlPart).toURI()).toAbsolutePath().normalize();
            if (entry.endsWith(".jar")) {
                return extractZipEntryToCache(outer, entry);
            }
            return outer.toString();
        } catch (URISyntaxException e) {
            throw new IOException("无效的 jar URL: " + jarUrl, e);
        }
    }

    /**
     * 从外层 zip/jar 中解压指定条目到缓存（带同步，避免并发写坏文件）。
     */
    private synchronized String extractZipEntryToCache(Path outerJar, String zipEntryPath) throws IOException {
        Files.createDirectories(nestedLibCacheDir);
        long mtime = Files.getLastModifiedTime(outerJar).toMillis();
        String cacheKey = outerJar.toAbsolutePath() + "|" + zipEntryPath + "|" + mtime;
        String fileName = String.format("%08x__%s", cacheKey.hashCode(),
                Paths.get(zipEntryPath).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_"));
        Path target = nestedLibCacheDir.resolve(fileName);
        if (!Files.exists(target) || Files.size(target) == 0L) {
            try (ZipFile zf = new ZipFile(outerJar.toFile())) {
                ZipEntry entry = zf.getEntry(zipEntryPath);
                if (entry == null) {
                    throw new IOException("ZIP 中不存在条目: " + zipEntryPath + " （外层: " + outerJar + "）");
                }
                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("已解压嵌套依赖供 javac 使用: {} -> {}", zipEntryPath, target);
            }
        }
        return target.toAbsolutePath().normalize().toString();
    }

    /**
     * 从类代码中提取类名
     */
    private String extractClassName(String classCode) {
        String[] lines = classCode.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("public class")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length; i++) {
                    if ("class".equals(parts[i]) && i + 1 < parts.length) {
                        String className = parts[i + 1];
                        // 移除可能的extends部分
                        if (className.contains(" ")) {
                            className = className.split("\\s+")[0];
                        }
                        return className;
                    }
                }
            }
        }
        throw new RuntimeException("无法从代码中提取类名");
    }

    /**
     * 从类代码中提取方法名
     */
    private static final Pattern STATIC_STRATEGY_METHOD = Pattern.compile(
            "public\\s+static\\s+Strategy\\s+(\\w+)\\s*\\(\\s*(?:final\\s+)?BarSeries\\s+\\w+\\s*\\)",
            Pattern.MULTILINE);

    private String extractMethodName(String classCode) {
        Matcher m = STATIC_STRATEGY_METHOD.matcher(classCode);
        if (m.find()) {
            return m.group(1);
        }
        throw new RuntimeException("无法从代码中提取方法名，请确保方法格式为：public static Strategy methodName(BarSeries series)");
    }

    /**
     * 将策略函数动态加载到StrategyFactory
     */
    private void loadStrategyToFactory(String strategyCode, Function<BarSeries, Strategy> strategyFunction) {
        try {
            // 通过反射获取StrategyRegisterCenter的strategyCreators字段
            Field strategyCreatorsField = com.okx.trading.strategy.StrategyRegisterCenter.class.getDeclaredField("strategyCreators");
            strategyCreatorsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Function<BarSeries, Strategy>> strategyCreators =
                    (Map<String, Function<BarSeries, Strategy>>) strategyCreatorsField.get(null);

            // 添加新策略
            strategyCreators.put(strategyCode, strategyFunction);

            log.info("策略 {} 已动态加载到StrategyRegisterCenter", strategyCode);
        } catch (Exception e) {
            log.error("动态加载策略到StrategyRegisterCenter失败: {}", e.getMessage(), e);
            throw new RuntimeException("动态加载策略失败: " + e.getMessage());
        }
    }

    /**
     * 从数据库加载所有动态策略
     */
    public void loadAllDynamicStrategies() {
        try {
            // 获取所有有源代码的策略
            strategyInfoService.findAll().stream()
                    .filter(strategy ->
                            strategy.getSourceCode() != null &&
                            !strategy.getSourceCode().trim().isEmpty() &&
                            strategy.getSourceCode().contains("public class"))
                    .forEach(strategy -> {
                        try {
                            compileAndLoadStrategy(strategy.getSourceCode(), strategy);
                            // 加载成功，清除之前的错误信息
                            if (strategy.getLoadError() != null) {
                                strategy.setLoadError(null);
                                strategyInfoService.saveStrategy(strategy);
                            }
                            log.info("使用Java Compiler API从数据库加载策略: {}", strategy.getStrategyCode());
                        } catch (Exception e) {
                            String errorMessage = "使用Java Compiler API加载策略失败: " + e.getMessage();
                            log.error("加载策略 {} 失败: {}", strategy.getStrategyCode(), e.getMessage());

                            // 将错误信息保存到数据库
                            try {
                                strategy.setLoadError(errorMessage);
                                strategyInfoService.saveStrategy(strategy);
                                log.info("策略 {} 的错误信息已保存到数据库", strategy.getStrategyCode());
                            } catch (Exception saveException) {
                                log.error("保存策略 {} 的错误信息失败: {}", strategy.getStrategyCode(), saveException.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("使用Java Compiler API加载动态策略失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 移除策略
     */
    public void removeStrategy(String strategyCode) {
        try {
            // 从缓存中移除
            compiledStrategies.remove(strategyCode);

            // 从StrategyRegisterCenter中移除
            Field strategyCreatorsField = com.okx.trading.strategy.StrategyRegisterCenter.class.getDeclaredField("strategyCreators");
            strategyCreatorsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Function<BarSeries, Strategy>> strategyCreators =
                    (Map<String, Function<BarSeries, Strategy>>) strategyCreatorsField.get(null);

            strategyCreators.remove(strategyCode);

            log.info("策略 {} 已移除", strategyCode);
        } catch (Exception e) {
            log.error("移除策略失败: {}", e.getMessage(), e);
            throw new RuntimeException("移除策略失败: " + e.getMessage());
        }
    }

    /**
     * 获取已编译的策略函数
     */
    public Function<BarSeries, Strategy> getCompiledStrategy(String strategyCode) {
        return compiledStrategies.get(strategyCode);
    }

    /**
     * 检查策略是否已加载
     */
    public boolean isStrategyLoaded(String strategyCode) {
        return compiledStrategies.containsKey(strategyCode);
    }

    /**
     * 清理临时文件
     */
    public void cleanup() {
        try {
            if (Files.exists(tempCompileDir)) {
                Files.walk(tempCompileDir)
                        .sorted((a, b) -> b.compareTo(a)) // 逆序，先删除文件再删除目录
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("删除临时文件失败: {}", path, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("清理临时编译目录失败", e);
        }
    }
}
