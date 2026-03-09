package org.noear.solon.bot.core.config;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ApiServerParameters implements Serializable {
    private String docUrl;
    private String apiBaseUrl;
    private Map<String, String> headers = new HashMap<>();
}
