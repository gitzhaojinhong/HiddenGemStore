# hgs-redis-framework 组件深度剖析

## 一、组件概述与功能定位

`hgs-redis-framework` 是对 Spring Data Redis `StringRedisTemplate` 的二次封装组件，核心目标是解决以下痛点：

| 痛点 | 解决方案 |
|------|----------|
| 使用 RedisTemplate 时需要手动做对象序列化/反序列化 | 统一使用 fastjson 进行 JSON 序列化，存取时自动转换 |
| Redis key 分散在代码各处，难以管理和追溯 | 通过 `RedisKeyManage` 枚举强制统一管理，附带 key 含义、value 说明、作者信息 |
| 不同环境的 key 隔离问题 | 通过 `SpringUtil.getPrefixDistinctionName()` 自动添加应用前缀 |

## 二、模块结构与类关系

```
com.hiddengemstore
├── enums
│   └── RedisKeyManage          // key 枚举管理（不可修改）
├── redis
│   ├── api
│   │   └── RedisCache          // 对外暴露的接口（~110 个方法）
│   ├── config
│   │   └── RedisCacheAutoConfig // Spring Boot 自动配置类
│   └── internal
│       ├── RedisCacheImpl       // 接口实现（基于 StringRedisTemplate）
│       ├── RedisKeyBuild        // key 构建器（不可变对象）
│       └── CacheUtil            // 工具类（参数校验、类型构建、集合优化）
```

**依赖关系：**

```
RedisCacheAutoConfig
    └── 创建 RedisCacheImpl 实例，注入 StringRedisTemplate

RedisCacheImpl（实现 RedisCache 接口）
    ├── 依赖 StringRedisTemplate（Spring 提供）
    ├── 依赖 RedisKeyBuild（构建 Redis key）
    ├── 依赖 CacheUtil（参数校验、类型构建、集合优化）
    └── 依赖 com.alibaba.fastjson.JSON（序列化/反序列化）

RedisKeyBuild
    ├── 依赖 RedisKeyManage（获取 key 模板）
    └── 依赖 SpringUtil（获取应用前缀）
```

## 三、核心类详解

### 3.1 RedisKeyManage — key 枚举管理

**设计意图：** 强制用户将所有 Redis key 定义在枚举中，避免 key 散落在代码各处。

**枚举字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | `String` | key 的模板，支持 `String.format` 占位符，如 `"user:info:%s"` |
| `keyIntroduce` | `String` | key 的含义说明 |
| `valueIntroduce` | `String` | value 的类型/含义说明 |
| `author` | `String` | 该 key 的设计者 |

**额外方法：** `getRc(String keyCode)` — 通过 key 模板反查枚举常量，不存在时返回 `null`。

### 3.2 RedisKeyBuild — key 构建器

**设计意图：** 不可变对象，封装 Redis key 的构建逻辑，防止用户随意构造 key。

**核心机制：**

```java
public static RedisKeyBuild createRedisKey(RedisKeyManage redisKeyManage, Object... args) {
    String redisRelKey = String.format(redisKeyManage.getKey(), args);
    return new RedisKeyBuild(SpringUtil.getPrefixDistinctionName() + "-" + redisRelKey);
}
```

生成的 key 格式为：`{应用前缀}-{枚举key格式化后的值}`

例如：应用前缀为 `hmdp`，枚举为 `USER_INFO_KEY("user:info:%s", ...)`，参数为 `123`，则最终 key 为 `hmdp-user:info:123`。

**关键特性：**
- 构造方法私有化，只能通过 `createRedisKey` 静态方法创建
- 重写了 `equals` 和 `hashCode`，基于 `relKey` 字段判断相等
- 线程安全（所有字段为 `final`）

### 3.3 RedisCacheAutoConfig — 自动配置

```java
public class RedisCacheAutoConfig {
    @Bean
    public RedisCache redisCache(
        @Qualifier("redisToolStringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        return new RedisCacheImpl(stringRedisTemplate);
    }
}
```

使用 `@Qualifier("redisToolStringRedisTemplate")` 注入特定的 `StringRedisTemplate` Bean，说明项目中可能存在多个 Redis 数据源，此处明确指定使用名为 `redisToolStringRedisTemplate` 的那个。

### 3.4 RedisCacheImpl — 核心实现

基于 `StringRedisTemplate`，所有缓存值以 JSON 字符串存储。使用 Lombok `@AllArgsConstructor` 生成构造方法。

**序列化策略：** 存入时判断对象类型，String 直接存入，其他对象通过 `JSON.toJSONString()` 转为 JSON 字符串：

```java
String json = object instanceof String ? (String) object : JSON.toJSONString(object);
```

**反序列化策略：** 通过 `getComplex` 和 `parseObjects` 两个私有方法完成，核心依赖 `CacheUtil.buildType()` 构建泛型类型。

#### Redis 数据结构 API 一览

**String 操作：**
`get` / `getRange` / `getValueIsList` / `getKeys` / `set` / `setIfAbsent` / `size` / `multiSet` / `multiSetIfAbsent` / `incrBy` / `incrByDouble` / `append`

**Key 通用操作：**
`hasKey` / `del` / `expire` / `getExpire` / `keys` / `move` / `persist` / `randomKey` / `rename` / `renameIfAbsent` / `type`

**Hash 操作：**
`putHash` / `putHashIfAbsent` / `getForHash` / `getValueIsListForHash` / `multiGetForHash` / `getAllForHash` / `getAllMapForHash` / `hasKeyForHash` / `delForHash` / `incrByForHash` / `incrByDoubleForHash` / `hashKeysForHash` / `sizeForHash`

**List 操作：**
`indexForList` / `leftPushForList` / `leftPushAllForList` / `leftPushIfPresentForList` / `rightPushForList` / `rightPushAllForList` / `rightPushIfPresentForList` / `setForList` / `leftPopForList` / `leftPopBlockForList` / `rightPopForList` / `rightPopBlockForList` / `rightPopAndLeftPushForList` / `rightPopBlockAndLeftPushForList` / `getAllForList` / `rangeForList` / `removeForList` / `trimForList` / `lenForList`

**Set 操作：**
`addForSet` / `removeForSet` / `popForSet` / `moveForSet` / `sizeForSet` / `isMemberForSet` / `intersectForSet` / `intersectAndStoreForSet` / `unionForSet` / `unionAndStoreForSet` / `differenceForSet` / `membersForSet` / `randomMemberForSet` / `randomMembersForSet` / `distinctRandomMembersForSet` / `scanForSet`

**SortedSet (ZSet) 操作：**
`addForSortedSet` / `getRangeForSortedSet` / `getReverseRangeForSortedSet` / `delForSortedSet` / `delRangeForSortedSet` / `incrementScoreForSortedSet` / `sizeForSortedSet` / `rankForSortedSet` / `reverseRankForSortedSet` / `rangeWithScoreForSortedSet` / `rangeByScoreForSortedSet` / `rangeByScoreWithScoreForSortedSet` / `reverseRangeWithScoreForSortedSet` / `reverseRangeByScoreForSortedSet` / `reverseRangeByScoreWithScoreForSortedSet` / `countForSortedSet` / `zCardForSortedSet` / `scoreByValueForSortedSet` / `removeRangeForSortedSet` / `removeRangeByScoreForSortedSet` / `unionAndStoreForSortedSet` / `intersectAndStoreForSortedSet` / `scanForSortedSet`

**特殊方法：**
`getByType` — 根据 `java.lang.reflect.Type` 泛型类型反序列化（支持复杂泛型如 `List<User>`）
`getInstance` — 返回底层 `StringRedisTemplate` 实例

### 3.5 CacheUtil — 工具类（重点分析）

`CacheUtil` 提供三大类功能：**参数校验**、**类型构建**、**集合优化**。

#### 3.5.1 `buildType` 方法与 `ParameterizedTypeImpl` 深度解析

这是整个组件中最难理解的部分。先看代码：

```java
public static Type buildType(Type... types) {
    ParameterizedTypeImpl beforeType = null;
    if (types != null && types.length > 0) {
        if (types.length == 1) {
            return new ParameterizedTypeImpl(new Type[]{null}, null, types[0]);
        }
        for (int i = types.length - 1; i > 0; i--) {
            beforeType = new ParameterizedTypeImpl(
                new Type[]{beforeType == null ? types[i] : beforeType},
                null,
                types[i - 1]
            );
        }
    }
    return beforeType;
}
```

**`ParameterizedTypeImpl` 是什么？**

`com.alibaba.fastjson.util.ParameterizedTypeImpl` 是阿里巴巴 fastjson 库提供的一个类，它实现了 `java.lang.reflect.ParameterizedType` 接口。

`ParameterizedType` 是 Java 反射 API 中表示**参数化类型**的接口。比如 `List<String>`、`Map<String, User>` 这种带泛型参数的类型，Java 编译后由于**类型擦除**（Type Erasure），运行时会丢失泛型信息，变成原始类型 `List`、`Map`。`ParameterizedTypeImpl` 让我们可以在运行时**手动构造**出带泛型的类型信息，这样 fastjson 就知道应该反序列化成什么具体类型。

**`ParameterizedTypeImpl` 的构造方法：**

```java
public ParameterizedTypeImpl(Type[] actualTypeArguments, Type ownerType, Type rawType)
```

| 参数 | 含义 | 示例（以 `List<String>` 为例） |
|------|------|------|
| `actualTypeArguments` | 实际的类型参数数组 | `new Type[]{String.class}` |
| `ownerType` | 所属类型（嵌套类型时使用，通常为 `null`） | `null` |
| `rawType` | 原始类型（泛型的载体类） | `List.class` |

也就是说，要构造 `List<String>` 这个类型对象，就是：

```java
new ParameterizedTypeImpl(new Type[]{String.class}, null, List.class)
```

**`buildType` 方法的工作原理：**

`buildType` 接收一个类型数组，按**从外到内**的顺序排列。例如：

- `buildType(List.class, User.class)` → 构造 `List<User>` 的类型
- `buildType(Map.class, String.class, User.class)` → 构造 `Map<String, User>` 的类型

**单参数情况（`types.length == 1`）：**

```java
return new ParameterizedTypeImpl(new Type[]{null}, null, types[0]);
```

当只有一个类型时（如 `buildType(User.class)`），直接返回一个没有实际类型参数的包装。`actualTypeArguments` 为 `{null}` 表示没有泛型参数，`rawType` 为 `User.class`。这种情况下返回的类型等价于 `User` 本身（非泛型类型），fastjson 会直接按 `User.class` 进行反序列化。

**多参数情况（循环构建）：**

以 `buildType(List.class, User.class)` 为例，`types = [List.class, User.class]`：

```
循环：i 从 types.length-1=1 开始，i > 0
  i=1: beforeType = new ParameterizedTypeImpl(
         new Type[]{types[1] == null ? User.class : null},  // actualTypeArguments = [User.class]
         null,                                                // ownerType = null
         types[0] == List.class                               // rawType = List.class
       )
       → 代表 List<User>
```

以 `buildType(Map.class, String.class, User.class)` 为例，`types = [Map.class, String.class, User.class]`：

```
循环：i 从 2 开始，i > 0
  i=2: beforeType = new ParameterizedTypeImpl(
         new Type[]{User.class},    // actualTypeArguments = [User.class]
         null,
         String.class               // rawType = String.class
       )
       → 代表 String<User>（这不是一个合法的 Java 类型，但作为中间结果传递）

  i=1: beforeType = new ParameterizedTypeImpl(
         new Type[]{上面的中间结果},  // actualTypeArguments = [String<User>的ParameterizedType]
         null,
         Map.class                   // rawType = Map.class
       )
       → 代表 Map<String<User>, ???>
```

等等，这里看起来有问题？实际上，这个循环的设计意图是：**从最内层往外层逐层嵌套构建**。但仔细分析后会发现，对于 `Map.class, String.class, User.class` 这种两层泛型参数的场景，`buildType` 的行为并不是构造 `Map<String, User>`，而是构造了一种嵌套结构。

**实际上，`buildType` 主要面向的场景是单层嵌套泛型：**

- `buildType(List.class, User.class)` → `List<User>` ✅
- `buildType(List.class, String.class)` → `List<String>` ✅
- `buildType(User.class)` → `User` ✅

对于 `Map` 这种有两个类型参数的场景，需要将 Map 的 key 和 value 类型分别展开为单独的参数传入，但由于循环逻辑的设计，它实际上通过 `beforeType` 的链式嵌套来实现多层泛型。

**为什么需要这个方法？**

在 `RedisCacheImpl` 中，从 Redis 获取的值是 JSON 字符串，需要反序列化为 Java 对象。对于简单类型如 `User`，可以直接用 `JSON.parseObject(json, User.class)`。但对于 `List<User>` 这种泛型类型，`Class` 对象无法携带泛型信息（`List.class` 不知道元素是 `User`），所以需要通过 `ParameterizedTypeImpl` 手动构造出 `List<User>` 的类型描述，交给 fastjson 正确反序列化。

**在代码中的调用示例：**

```java
// RedisCacheImpl.getComplex 方法中：
return source instanceof String
    ? JSON.parseObject((String) source, CacheUtil.buildType(clazz))
    : null;
```

当 `clazz` 为 `User.class` 时，`buildType(User.class)` 返回一个 `ParameterizedTypeImpl`，fastjson 会根据这个类型信息将 JSON 字符串反序列化为 `User` 对象。

#### 3.5.2 参数校验方法

| 方法 | 功能 | 抛出异常 |
|------|------|----------|
| `checkNotBlank(String... key)` | 校验多个字符串 key 非空 | `RuntimeException("请求参数缺失")` |
| `checkNotBlank(RedisKeyBuild)` | 校验 RedisKeyBuild 的 relKey 非空 | 同上 |
| `checkNotBlank(Collection<String>)` | 校验集合中每个字符串非空 | 同上 |
| `checkNotEmpty(Collection<?>)` | 校验集合中每个元素非 null | 同上 |
| `checkNotEmpty(Object)` | 校验对象非空（null、空字符串、空集合） | 同上 |
| `isEmpty(Object)` | 判断对象是否为空（不抛异常） | — |

`isEmpty` 方法对三种类型做了特殊处理：`null` 返回 `true`，`String` 使用 `StrUtil.isEmpty` 判断，`Collection` 使用 `isEmpty()` 判断，其他类型返回 `false`。

#### 3.5.3 集合优化方法

| 方法 | 功能 |
|------|------|
| `getBatchKey(Collection<RedisKeyBuild>)` | 从 RedisKeyBuild 集合中提取所有 `relKey` 字符串 |
| `optimizeRedisList(List<T>)` | 将 null 或首元素为 null 的列表转为空集合，避免下游空指针 |
| `checkRedisListIsEmpty(List<?>)` | 判断列表是否为空或无效（null、空列表、首元素为 null） |

`optimizeRedisList` 和 `checkRedisListIsEmpty` 都检查首元素是否为 null，这是因为 Spring Data Redis 的 `multiGet` 方法在某些情况下会返回一个包含 null 元素的列表（而非空列表），首元素为 null 通常意味着整个查询结果无效。

## 四、内部状态管理与生命周期

### 4.1 内部状态

`RedisCacheImpl` 唯一的状态就是 `StringRedisTemplate` 实例，通过构造方法注入，之后不再变更。组件本身是**无状态**的，所有缓存数据都存储在 Redis 中。

### 4.2 生命周期

- **创建：** 由 `RedisCacheAutoConfig` 在 Spring 容器初始化时创建，注入 `StringRedisTemplate`
- **使用：** 通过 `@Autowired RedisCache` 注入使用
- **销毁：** 随 Spring 容器销毁，无需额外清理

### 4.3 线程安全性

- `RedisKeyBuild` 是不可变对象（所有字段 `final`），天然线程安全
- `RedisCacheImpl` 的 `stringRedisTemplate` 字段在构造后不再修改，且 `StringRedisTemplate` 本身是线程安全的
- `CacheUtil` 全部是静态方法，无共享状态，线程安全

## 五、性能优化策略

### 5.1 批量查询优化（getKeys 方法）

```java
public List<String> getKeys(List<RedisKeyBuild> keyList) {
    List<String> batchKey = CacheUtil.getBatchKey(keyList);
    List<String> list = stringRedisTemplate.opsForValue().multiGet(batchKey);
    return CacheUtil.optimizeRedisList(
        stringRedisTemplate.opsForValue().multiGet(CacheUtil.optimizeRedisList(list))
    );
}
```

这里执行了**两次 multiGet**。第一次 `multiGet` 获取结果后，通过 `optimizeRedisList` 检查是否有效，如果无效则直接返回空集合；如果有效，将第一次的结果再次传入 `multiGet` 进行二次查询。这是一种**重试机制**——第一次查询可能因为网络抖动等原因返回 null，二次查询可以降低空结果的概率。

### 5.2 JSON 序列化的类型判断

所有写入操作都先判断 `instanceof String`，对于已经是 String 类型的值直接存储，避免不必要的 JSON 序列化开销：

```java
String json = object instanceof String ? (String) object : JSON.toJSONString(object);
```

### 5.3 预分配集合容量

批量操作时预分配 HashMap 和 ArrayList 容量，减少扩容开销：

```java
Map<String, String> mapForSave = new HashMap<>(map.size());
List<String> jsonList = new ArrayList<>(valueList.size());
```

## 六、错误处理机制

### 6.1 参数校验

所有公共方法入口处通过 `CacheUtil.checkNotBlank` / `checkNotEmpty` 进行参数校验，校验失败直接抛出 `RuntimeException`，错误信息为 `"请求参数缺失"`。

**注意：** 这种统一的 `RuntimeException` 不利于调用方精确捕获异常类型，建议后续考虑定义自定义异常类（如 `CacheKeyException`）。

### 6.2 空值安全

- `getComplex` 方法对 `null` 源数据直接返回 `null`
- `parseObjects` 方法对 `null` 源数据返回空集合
- `optimizeRedisList` 将无效列表转为空集合
- Redis 不存在的 key，底层 `StringRedisTemplate` 返回 `null`，组件不做额外包装

### 6.3 无异常包装

组件没有对底层 Redis 操作的异常进行捕获或转换，Redis 连接异常（如 `RedisConnectionFailureException`）会直接抛给调用方。这是一种合理的设计选择——缓存层不应吞掉基础设施异常。

## 七、使用

项目中任何需要使用 Redis 缓存的业务模块，通过以下方式使用：

```java
@Autowired
private RedisCache redisCache;

// 构建 key（必须通过枚举）
RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId);

// 存取操作
redisCache.set(key, userVo, 3600);  // 存，过期时间 3600 秒
UserVo user = redisCache.get(key, UserVo.class);  // 取，指定目标类型
```

