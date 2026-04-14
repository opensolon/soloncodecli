# Security — 安全（认证/鉴权/CORS/加密）

> 适用场景：跨域处理、用户认证、路径鉴权、角色权限控制。

## CORS — 跨域处理

Dependency: `solon-web-cors`（已包含在 `solon-web` 中）

### 方式一：注解在控制器或方法上

```java
@CrossOrigin(origins = "*")
@Controller
public class DemoController {
    @Mapping("/hello")
    public String hello() { return "hello"; }
}
```

### 方式二：注解在基类

```java
@CrossOrigin(origins = "*")
public class BaseController {}

@Controller
public class DemoController extends BaseController {
    @Mapping("/hello")
    public String hello() { return "hello"; }
}
```

### 方式三：全局配置

```java
Solon.start(App.class, args, app -> {
    // 全局处理（过滤器模式，-1 优先级更高）
    app.filter(-1, new CrossFilter().allowedOrigins("*"));

    // 某段路径
    app.filter(new CrossFilter().pathPatterns("/user/**").allowedOrigins("*"));

    // 路由拦截器模式
    app.routerInterceptor(-1, new CrossInterceptor().allowedOrigins("*"));
});
```

---

## Auth — 用户认证与鉴权

Dependency: `solon-security-auth`

核心概念：通过 `AuthAdapter` 统一配置认证规则，通过 `AuthProcessor` 接口适配具体业务逻辑。

### 第 1 步：构建认证适配器

```java
@Configuration
public class Config {
    @Bean(index = 0)
    public AuthAdapter init() {
        return new AuthAdapter()
                .loginUrl("/login")
                .addRule(r -> r.include("**").verifyIp()
                        .failure((c, t) -> c.output("你的IP不在白名单")))
                .addRule(b -> b.exclude("/login**").exclude("/run/**").verifyPath())
                .processor(new AuthProcessorImpl())
                .failure((ctx, rst) -> ctx.render(rst));
    }
}
```

规则配置说明：
- `include(path)` — 规则包含的路径范围
- `exclude(path)` — 规则排除的路径范围
- `failure(..)` — 规则失败后的处理
- `verifyIp()` / `verifyPath()` / `verifyLogined()` — 验证方案

### 第 2 步：认证异常处理

```java
@Component
public class DemoFilter implements Filter {
    @Override
    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        try {
            chain.doFilter(ctx);
        } catch (AuthException e) {
            AuthStatus status = e.getStatus();
            ctx.render(Result.failure(status.code, status.message));
        }
    }
}
```

### 第 3 步：实现认证处理器

```java
public class AuthProcessorImpl implements AuthProcessor {
    @Override
    public boolean verifyIp(String ip) {
        // 验证 IP 是否有权访问
        return true;
    }

    @Override
    public boolean verifyLogined() {
        // 验证用户是否已登录
        return getSubjectId() > 0;
    }

    @Override
    public boolean verifyPath(String path, String method) {
        // 验证路径，用户是否可访问
        return true;
    }

    @Override
    public boolean verifyPermissions(String[] permissions, Logical logical) {
        // 验证特定权限（verifyLogined 为 true 时触发）
        return true;
    }

    @Override
    public boolean verifyRoles(String[] roles, Logical logical) {
        // 验证特定角色（verifyLogined 为 true 时触发）
        return true;
    }
}
```

### 注解控制（特定权限/角色）

```java
@Controller
@Mapping("/demo/agroup")
public class AgroupController {
    @Mapping("")
    public void home() { /* 首页 */ }

    @AuthPermissions("agroup:edit")
    @Mapping("edit/{id}")
    public void edit(int id) { /* 需要编辑权限 */ }

    @AuthRoles("admin")
    @Mapping("edit/{id}/ajax/save")
    public void save(int id) { /* 需要管理员角色 */ }
}
```

### 模板中使用

```html
<@authPermissions name="user:del">我有 user:del 权限</@authPermissions>
<@authRoles name="admin">我有 admin 角色</@authRoles>
```

### 组合使用建议

- **规则控制**：在 AuthAdapter 中配置所有需要登录的地址（宏观控制）
- **注解控制**：在特定方法上控制权限和角色（细节把握）

---

## Vault — 配置加密

Dependency: `solon-security-vault`

用于敏感配置项的加密存储（如数据库连接信息），让敏感信息不直接暴露在配置文件中。

### 配置密码

```yaml
solon.vault:
  password: "liylU9PhDq63tk1C"  # 默认算法要求 16 位，建议包含大小写和数字
```

密码也可通过启动参数传入（更安全）：

```bash
java -Dsolon.vault.password=xxx -jar demo.jar
```

### 生成密文

```java
public class App {
    public static void main(String[] args) {
        Solon.start(App.class, args);
        // 打印生成的密文
        System.out.println(VaultUtils.encrypt("root"));
    }
}
```

### 使用密文配置

```yaml
solon.vault:
  password: "liylU9PhDq63tk1C"

test.db1:
  url: "jdbc:mysql://localhost:3306/test"
  username: "ENC(xo1zJjGXUouQ/CZac55HZA==)"
  password: "ENC(XgRqh3C00JmkjsPi4mPySA==)"
```

### 注解注入（@VaultInject）

```java
@Configuration
public class TestConfig {
    @Bean("db2")
    private DataSource db2(@VaultInject("${test.db1}") HikariDataSource ds) {
        return ds;
    }
}
```

### 手动解密

```java
// 解密一块配置
Props props = Solon.cfg().getProp("test.db1");
VaultUtils.guard(props);
HikariDataSource ds = props.getBean(HikariDataSource.class);

// 解密单个配置
String name = VaultUtils.guard(Solon.cfg().get("test.demo.name"));
```

### 定制加密算法

```java
@Component
public class VaultCoderImpl implements VaultCoder {
    private final String password;

    public VaultCoderImpl() {
        this.password = Solon.cfg().get("solon.vault.password");
    }

    @Override
    public String encrypt(String str) throws Exception {
        // 自定义加密实现
        return null;
    }

    @Override
    public String decrypt(String str) throws Exception {
        // 自定义解密实现
        return null;
    }
}
```

---

## Web 安全 — 请求头安全

Dependency: `solon-security-web`（v3.1.1 后支持）

提供 HTTP 请求头安全防护能力。`SecurityFilter` 是一个 web 过滤器，可组织多种 Handler 进行安全处理。

### 安全处理器列表

| Handler | 说明 |
|---------|------|
| `CacheControlHeadersHandler` | `Cache-Control` 头处理器 |
| `HstsHeaderHandler` | `Strict-Transport-Security` 头处理器 |
| `XContentTypeOptionsHeaderHandler` | `X-Content-Type-Options` 头处理器 |
| `XFrameOptionsHeaderHandler` | `X-Frame-Options` 头处理器 |
| `XXssProtectionHeaderHandler` | `X-XSS-Protection` 头处理器 |

### 使用示例

```java
@Configuration
public class DemoFilter {
    @Bean(index = -99)
    public SecurityFilter securityFilter() {
        return new SecurityFilter(
                new XContentTypeOptionsHeaderHandler(),
                new XXssProtectionHeaderHandler()
        );
    }
}
```

---

## Validation — 请求参数校验

Dependency: `solon-security-validation`

提供请求参数校验能力，支持 Context 参数校验（注入前校验）和实体字段校验（注入后校验）两种模式。

### 基本用法

```java
@Valid  // 启用校验（加在控制器类上或基类上）
@Controller
public class UserController {
    @NoRepeatSubmit  // 重复提交验证（方法级，注入前校验）
    @Whitelist       // 白名单验证（方法级，注入前校验）
    @Mapping("/user/add")
    public void addUser(
            @NotNull String name,
            @Pattern("^http") String icon,
            @Validated User user) {  // 实体校验需加 @Validated
        // ...
    }

    // 分组校验
    @Mapping("/user/update")
    public void updateUser(@Validated(UpdateLabel.class) User user) {
        // ...
    }
}
```

### 实体字段校验

```java
@Data
public class User {
    @NotNull(groups = UpdateLabel.class)  // 分组校验
    private Long id;

    @NotNull
    private String nickname;

    @Email
    private String email;

    @Validated          // 验证列表里的实体
    @NotNull
    @Size(min = 1)      // 最少要有1个
    private List<Order> orderList;
}
```

### 工具手动校验

```java
User user = new User();
ValidUtils.validateEntity(user);
```

### 全量校验配置

默认策略：有校验不通过时马上返回。如需校验所有字段，添加配置：

```yaml
solon.validation.validateAll: true
```

### 校验注解一览

| 注解 | 作用范围 | 说明 |
|------|---------|------|
| `@Valid` | 控制器类 | 启用校验能力 |
| `@Validated` | 参数 或 字段 | 校验实体（或实体集合）上的字段 |
| `@Date` | 参数 或 字段 | 校验值为日期格式 |
| `@DecimalMax(value)` | 参数 或 字段 | 校验值 <= 指定值 |
| `@DecimalMin(value)` | 参数 或 字段 | 校验值 >= 指定值 |
| `@Email` | 参数 或 字段 | 校验值为电子邮箱格式 |
| `@Length(min, max)` | 参数 或 字段 | 校验值长度在区间内（对字符串有效） |
| `@Logined` | 控制器 或 动作 | 校验请求主体已登录 |
| `@Max(value)` | 参数 或 字段 | 校验值 <= 指定值 |
| `@Min(value)` | 参数 或 字段 | 校验值 >= 指定值 |
| `@NoRepeatSubmit` | 控制器 或 动作 | 校验请求未重复提交 |
| `@NotBlacklist` | 控制器 或 动作 | 校验请求主体不在黑名单 |
| `@NotBlank` | 动作/参数/字段 | 校验值不是空白（String） |
| `@NotEmpty` | 动作/参数/字段 | 校验值不是空（String） |
| `@NotNull` | 动作/参数/字段 | 校验值不是 null |
| `@NotZero` | 动作/参数/字段 | 校验值不是 0 |
| `@Null` | 动作/参数/字段 | 校验值是 null |
| `@Numeric` | 动作/参数/字段 | 校验值为数字格式 |
| `@Pattern(value)` | 参数 或 字段 | 校验值匹配指定正则 |
| `@Size(min, max)` | 参数 或 字段 | 校验集合大小在区间内 |
| `@Whitelist` | 控制器 或 动作 | 校验请求主体在白名单内 |

> 注：可作用在 [动作 或 参数] 上的注解，加在动作上时可支持多个参数的校验。

### 校验异常处理

通过过滤器捕捉校验异常：

```java
@Component
public class ValidatorFailureFilter implements Filter {
    @Override
    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        try {
            chain.doFilter(ctx);
        } catch (ValidatorException e) {
            ctx.render(Result.failure(e.getCode(), e.getMessage()));
        }
    }
}
```

### 定制校验

#### @NoRepeatSubmit 改为分布式锁

```java
@Component
public class NoRepeatSubmitCheckerNew implements NoRepeatSubmitChecker {
    @Override
    public boolean check(NoRepeatSubmit anno, Context ctx, String submitHash, int limitSeconds) {
        return LockUtils.tryLock(Solon.cfg().appName(), submitHash, limitSeconds);
    }
}
```

#### @Whitelist 实现白名单验证

```java
@Component
public class WhitelistCheckerNew implements WhitelistChecker {
    @Override
    public boolean check(Whitelist anno, Context ctx) {
        String ip = ctx.realIp();
        // 实现白名单逻辑
        return true;
    }
}
```

### 扩展自定义校验注解

#### 1. 定义注解

```java
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Date {
    String value() default "";      // 日期表达式
    String message() default "";   // 提示消息
    Class<?>[] groups() default {}; // 校验分组
}
```

#### 2. 实现校验器

```java
public class DateValidator implements Validator<Date> {
    @Override
    public String message(Date anno) {
        return anno.message();
    }

    @Override
    public Class<?>[] groups(Date anno) {
        return anno.groups();
    }

    /** 校验实体字段（注入后校验） */
    @Override
    public Result validateOfValue(Date anno, Object val0, StringBuilder tmp) {
        if (val0 != null && !(val0 instanceof String)) {
            return Result.failure();
        }
        String val = (String) val0;
        if (verify(anno, val)) {
            return Result.succeed();
        } else {
            return Result.failure();
        }
    }

    /** 校验上下文参数（注入前校验） */
    @Override
    public Result validateOfContext(Context ctx, Date anno, String name, StringBuilder tmp) {
        String val = ctx.param(name);
        if (verify(anno, val)) {
            return Result.succeed();
        } else {
            return Result.failure(name);
        }
    }

    private boolean verify(Date anno, String val) {
        if (Utils.isEmpty(val)) {
            return true; // 为空算通过，由 @NotNull 等进一步控制
        }
        try {
            if (Utils.isEmpty(anno.value())) {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(val);
            } else {
                DateTimeFormatter.ofPattern(anno.value()).parse(val);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
```

#### 3. 注册校验器

```java
@Configuration
public class Config {
    @Bean
    public void adapter() {
        ValidatorManager.register(Date.class, new DateValidator());
    }
}
```
