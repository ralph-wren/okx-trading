package com.okx.trading;

import com.okx.trading.config.PortCheckConfig;
import com.okx.trading.util.SystemUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * OKX交易API应用程序主类
 * 提供与OKX交易所API交互的功能
 */
@SpringBootApplication
@EnableScheduling
@Slf4j
@EnableAsync
public class OkxTradingApplication {

    /**
     * 应用默认端口
     */
    private static final int DEFAULT_PORT = 8088;

    /**
     * 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {

        // 启动Spring应用
        SpringApplication app = new SpringApplication(OkxTradingApplication.class);
        app.addListeners(new PortCheckConfig());  // 注册监听器
        Environment env = app.run(args).getEnvironment();

        String port = env.getProperty("server.port");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        log.info("\n----------------------------------------------------------\n" +
                        "应用 '{}' 已成功启动! 访问URL:\n" +
                        "本地: \thttp://localhost:{}{}\n" +
                        "外部: \thttp://{}:{}{}\n" +
                        "swagger: http://localhost:8088/swagger-ui/index.html\n" +
                        "----------------------------------------------------------",
                env.getProperty("spring.application.name", "okx-trading"),
                port,
                contextPath,
                "127.0.0.1",
                port,
                contextPath);
    }

    /**
     * 从命令行参数中获取端口号
     *
     * @param args 命令行参数
     * @return 端口号字符串，如果未指定则返回null
     */
    private static String getPortFromArgs(String[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--server.port=")) {
                    return args[i].substring("--server.port=".length());
                } else if (args[i].equals("--server.port") && i + 1 < args.length) {
                    return args[i + 1];
                }
            }
        }
        return null;
    }
}
