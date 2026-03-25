/*
 * Copyright 2017-2026 noear.org and authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.noear.solon.codecli.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Solon Code 配置加载器
 * <p>
 * 三级配置优先级: 当前目录 .soloncode/config.yml > 全局 ~/.soloncode/config.yml > jar 内置 config.yml
 *
 * @author noear
 * @since 2.0
 */
public class ConfigLoader {

    private static final String USER_HOME = System.getProperty("user.home");

    /**
     * 加载最优配置文件路径
     * <p>
     * 优先级: 当前目录 > 全局 HOME > null(使用 jar 内置)
     *
     * @return 配置文件路径，如果没有外部配置则返回 null
     */
    public static Path loadConfig() {
        // 1. 当前目录的配置（项目级）
        Path currentDirConfig = Paths.get(System.getProperty("user.dir"), ".soloncode", "config.yml");
        if (Files.exists(currentDirConfig)) {
            return currentDirConfig;
        }

        // 2. 全局配置
        Path globalConfig = getGlobalConfigPath();
        if (Files.exists(globalConfig)) {
            return globalConfig;
        }

        // 3. 没有外部配置，返回 null（Solon 会使用 classpath 内置的 config.yml）
        return null;
    }

    /**
     * 获取全局配置路径
     *
     * @return ~/.soloncode/config.yml
     */
    public static Path getGlobalConfigPath() {
        return Paths.get(USER_HOME, ".soloncode", "config.yml");
    }

    /**
     * 获取全局配置目录
     *
     * @return ~/.soloncode/
     */
    public static Path getGlobalConfigDir() {
        return Paths.get(USER_HOME, ".soloncode");
    }

    /**
     * 确保全局配置目录存在
     */
    public static void ensureGlobalConfigDir() {
        Path dir = getGlobalConfigDir();
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                throw new RuntimeException("无法创建全局配置目录: " + dir, e);
            }
        }
    }
}
