# Spring
* ### [Spring Framework vs Spring Boot](#spring-framework-vs-spring-boot)
* ### [@Transactional и механизмы проксирования](#transactional-и-механизмы-проксирования)
* ### [@Spring Security](#spring-security)
* ### [Пагинация в Spring Data](#пагинация-в-spring-data)
* ### [Динамическая фильтрация](#динамическая-фильтрация)

## Spring Framework vs Spring Boot

> **Ключевой момент:** Spring — это огромный конструктор, где ты сам решаешь, какие детали нужны и как их соединить. Spring Boot — это «собраная модель» с инструкцией, которая заводится одной кнопкой.

### 1. Spring Framework
Это ядро. Появился гораздо раньше. Главные фичи: **IoC** (Inversion of Control) и **DI** (Dependency Injection).
* **Проблема:** Требовал огромного количества XML или Java-конфигураций. Нужно было самому настраивать каждый бин, сервер, подключение к БД.

### 2. Spring Boot (Надстройка над Spring)
Не заменяет Spring, а использует его внутри.
* **Стартеры (Starters):** Наборы зависимостей. Вместо того чтобы подключать 10 библиотек для веба вручную, ты берешь один `spring-boot-starter-web`.
* **Автоконфигурация:** Механизм, который смотрит в твой `pom.xml`. Если он видит там драйвер БД, он сам создаст бин `DataSource` с дефолтными настройками.
* **Встроенный сервер:** Tomcat, Jetty или Undertow "вшиты" прямо в `.jar` файл приложения.

### 3. Веб-сервер в Spring Boot
Веб-сервер **не обязателен**. Приложение может быть консольным (`CommandLineRunner`).
* **Как отключить веб-слой:**
    * В `application.properties`: `spring.main.web-application-type=none`.
    * Через настройки: `spring.main.web-application-type=none` в `application.properties`.
    * В коде:
  ```java
  SpringApplication app = new SpringApplication(MyApplication.class);
  app.setWebApplicationType(WebApplicationType.NONE);
  app.run(args);
  
---

## @Transactional и механизмы проксирования

> **Ключевой момент:** Spring не оборачивает бин в **Proxy-объект**. Когда кто-то вызывает метод, он сначала попадает в Прокси, тот открывает транзакцию, вызывает реальный метод, и в конце закрывает транзакцию.

### 1. Проблема самовызова (Self-invocation)
Если внутри одного класса метод `А` вызывает метод `Б` (с `@Transactional`), транзакция **не откроется**.
* **Почему:** Вызов идет через `this`, то есть напрямую внутри оригинального объекта, минуя Прокси.
* **Решение:** Вызывать метод `Б` только из другого бина.

### 2. Как Spring подменяет объекты?
В жизненном цикле бина есть этап **BeanPostProcessor**. Именно здесь Spring решает: "Ага, у этого класса есть `@Transactional`, я не положу в контекст оригинал, я создам наследника (через CGLIB) или реализацию интерфейса (JDK Proxy) и положу её вместо оригинала".
* Мы всегда работаем с прокси, если берем объект из контекста.

```java
public class MyServiceProxy extends MyService {

    // Внутри прокси лежит ссылка на реальный объект
    private MyService realObject; 

    public MyServiceProxy(MyService realObject) {
        this.realObject = realObject;
    }

    @Override
    public void methodB() {
        System.out.println("LOG: Открываем транзакцию...");
        try {
            realObject.methodB(); // ВЫЗОВ РЕАЛЬНОГО МЕТОДА
            System.out.println("LOG: Делаем коммит...");
        } catch (Exception e) {
            System.out.println("LOG: Ролбэк транзакции!");
            throw e;
        }
    }

    @Override
    public void methodA() {
        // Метод А не помечен @Transactional, поэтому прокси просто пробрасывает вызов
        realObject.methodA(); 
    }
}
```
### 3. Почему methodA вызывающий methodB ломается?
Когда ты вызываешь methodA(), ты попадаешь в прокси. Прокси вызывает realObject.methodA().
Но внутри realObject.methodA() написано:
```java
public void methodA() {
this.methodB(); // THIS — это realObject, а не прокси
}
```

Мы вышли из прокси в реальный объект и внутри него работаем через this. Прокси больше не контролирует вызов.

### 3. Как получить прокси внутри самого бина?
Иногда нужно вызвать свой же транзакционный метод так, чтобы транзакция сработала. Чтобы «достучаться» до прокси изнутри того же класса, нужно либо внедрить самого себя (`@Autowired MyService self`), либо вытащить себя из `ApplicationContext`. Тогда вызов пойдет не через `this`, а через прокси-обертку.

**Self-injection (Внедрение самого себя):**
   ```java
   @Service
   public class MyService {
       @Autowired
       private MyService self; // Spring внедрит сюда ПРОКСИ, а не оригинал

       public void methodA() {
           self.methodB(); // Теперь вызов идет через прокси!
       }

       @Transactional
       public void methodB() { ... }
   }

```
---

## Spring Security

> **Ключевой момент:** Spring Security — это сложная цепочка фильтров (Security Filter Chain). Независимо от способа (сессия или JWT), цель одна: идентифицировать пользователя и положить его данные в **SecurityContextHolder** (ThreadLocal) и решить, можно ли пользователю войти в конкретный метод (**Authorization**).

### 1. Архитектура и точка входа
Когда в проекте подключается `spring-boot-starter-security`, Spring Boot автоматически регистрирует в сервлетном контейнере (Tomcat) специальный фильтр **DelegatingFilterProxy**.
* Он перенаправляет все входящие HTTP-запросы в систему Spring Security.
* Основная конфигурация настраивается через бин **SecurityFilterChain**, где с помощью `HttpSecurity` описываются правила авторизации URL, логика Logout и поведение CSRF-фильтра.

### 2. Authentication: Как сервер узнает пользователя?
Для управления аутентификацией необходимо настроить один или несколько **AuthenticationProvider**.
* **DaoAuthenticationProvider:** Стандартный провайдер для работы с БД. Ему требуется реализовать интерфейс **UserDetailsService**, который загружает данные пользователя.
* **Другие провайдеры:** Существуют провайдеры для LDAP, OAuth2 или кастомные (например, для работы с **JWT**, где мы сами валидируем токен).

### 3. Способы хранения состояния

#### Вариант А: Stateful (Session-based) Сессионный подход
Основан на хранении состояния пользователя на стороне сервера.

*   **Механика:** При успешной аутентификации сервер создает **HttpSession**, который для авторизации последующих запросов, хранит объект **SecurityContext** с настроенным объектом **Authentication** содержащий:
   * **Principal:** Наш UserDetails (объект пользователя).
   * **Authorities:** Роли и права пользователя.
*   **Authentication:** Создается **DAO-провайдером** через реализацию `UserDetailsService`. Процесс выглядит так: сервис достает хешированный пароль из базы, хеширует пришедший от пользователя пароль и сравнивает их в одном из фильтров.
*   **Контекст:** При успехе объект `Authentication` заполняется данными и добавляется в **ThreadLocal**: `SecurityContextHolder.getContext().setAuthentication(auth)`. Если проверка не прошла — выкидывается ошибка **401 Unauthorized**.
*   **Цепочка:** После этого запрос отправляется по цепочке (`filterChain.doFilter`). Последний фильтр перед контроллером — **FilterSecurityInterceptor** — проверяет авторизацию (например, через `@PreAuthorize`), запрашивая данные из `SecurityContextHolder`. Если роли не соответствуют — выкидывается **403 Forbidden**.
*   **JSESSIONID:** Это идентификатор сессии, который генерирует сервер. Под этим ключом объект `Authentication` хранится в `HttpSession`. Браузер присылает этот ID в куках, и Spring использует его для восстановления `SecurityContext` из памяти сервера в `SecurityContextHolder` для каждого нового запроса. Это и есть **хранение состояния**.

#### Визуальная схема Stateful
```text
[ Браузер (Клиент) ]          [ Сервер (Tomcat + Spring) ]
|                               |
|--- 1. POST /login ----------->|
|    (Логин/Пароль)             |--> DAO Провайдер + UserDetailsService
|                               |    (Достает хеш из БД, сравнивает)
|                               |           |
|<-- 2. SET-COOKIE -------------| <--- [ Успех! Создан Authentication ]
|    (JSESSIONID=ABC123)        |           |
|                               |     [ Сохранение в HttpSession ]
|                               |     (Ключ: SPRING_SECURITY_CONTEXT)
|                               |           |
|--- 3. GET /orders ----------->|     [ Восстановление контекста ]
|    (Cookie: JSESSIONID=ABC123)|<--- (Берет Auth из Session по ID)
|                               |           |
|                               |    [ SecurityContext в ThreadLocal ]
|                               |           |
|                               |    [ FilterSecurityInterceptor ]
|                               |    (Смотрит @PreAuthorize, сверяет роли)
|                               |           |
|<-- 4. HTTP 200 (Данные) ------| <--- [ Вызов метода контроллера ]
```

**Пример реализации UserDetailsService:**
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .map(user -> new CustomUserDetails(
                        user.getId(),
                        user.getEmail(),
                        user.getPassword(),
                        Collections.singleton(user.getRole()) // Роли превращаются в Authorities
                ))
                .orElseThrow(() -> new UsernameNotFoundException("Failed to retrieve user: " + username));
    }
}
```

#### Вариант Б: Stateless (JWT-based): Токенный подход (JWT)
Реализует принципы REST — при которых сервер не хранит состояние (сессию) и запрос должен содержать всю необходимую информацию для обработки. 

*   **Механика:** Специальной настройкой (`SessionCreationPolicy.STATELESS`) отключается создание `HttpSession`. Запросы приходят с данными об авторизации в заголовке `Authorization`.
*   **Bearer Token:** Из заголовка достается токен — закодированный в Base64 набор символов. Префикс `Bearer ` (6 символов + пробел) отрезается через `authHeader.substring(7)`.
*   **Валидация:** Проверка происходит вручную в **JwtFilter** (или через `JwtAuthenticationProvider`). При помощи класса `io.jsonwebtoken.Jwts` + секретный ключ + данных в токене, сервер формирует хэш и сравнивает его с хешем подписи токена.
*   **Доверие:** Сервер доверяет данным в токене (имя, роли), потому что любая попытка изменить их (например, подменить роль в теле токена) сделает подпись невалидной. Для генерации корректной подписи нужен секретный ключ, который есть только у сервера.
*   **Процесс:** Если подписи совпали, данные извлекаются, при помощи класса `io.jsonwebtoken.Claims`, создается и наполняется объект `Authentication` и на текущий запрос, сохраняется в ThreadLocal (тагже как в Stateful), после чего проверяются роли перед контроллером. Аутентификация происходит заново при каждом запросе.

#### Визуальная схема Stateless

```text
[ Клиент (Frontend) ]         [ Сервер (REST API) ]
|                               |
|--- 1. POST /login ----------->|
|    (Логин/Пароль)             |--> Аутентификация (БД или Keycloak)
|                               |           |
|<-- 2. HTTP 200 (Body) --------| <--- [ Генерация JWT с ролями ]
|    (Access Token)             |      (Подписан Секретным Ключом)
|                               |
|--- 3. GET /orders ----------->|    [ JwtFilter (Низкий уровень) ]
|    (Header: Bearer <JWT>)     |-- 1. Отрезает substring(7)
|                               |-- 2. Берет Данные токена + Секретный Ключ
|                               |-- 3. ВЫЧИСЛЯЕТ ХЕШ, сравнивает с тем что в токене  (Проверка подписи)
|                               |      (Доверяет данным, если хеш совпал)
|                               |-- 4. Наполняет SecurityContextHolder
|                               |      (Authentication в ThreadLocal)
|                               |           |
|                               |    [ FilterSecurityInterceptor ]
|                               |    (Проверка @PreAuthorize по ролям)
|                               |           |
|<-- 4. HTTP 200 (Данные) ------| <--- [ Вызов метода контроллера ]
|
|--- [ СЕРВЕР ЗАБЫЛ О ПОЛЬЗОВАТЕЛЕ ]
```

**Пример реализации низкоуровневого JwtFilter:**

```java
public class JwtFilter extends OncePerRequestFilter {
    // Секретный ключ, известный только серверу
    private String secretKey = "your-256-bit-secret-key-here-very-long-string";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Отрезаем "Bearer "
            try {
                // На сервере берутся данные из токена и секретный ключ 
                // Генерируется подпись и проверяется с той, что прислана в токене
                Claims claims = Jwts.parser()
                        .setSigningKey(secretKey)
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.getSubject();
                List<String> roles = claims.get("roles", List.class);

                if (username != null) {
                    // Создаем объект Authentication и доверяем данным, т.к. подпись совпала
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        username, null, roles.stream().map(SimpleGrantedAuthority::new).toList()
                    );
                    // Помещаем в ThreadLocal на время выполнения запроса
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Если подписи не совпали или токен просрочен — контекст останется пустым
            }
        }
        chain.doFilter(request, response);
    }
}
```

### 4. JWT структура

Токен представляет собой строку, состоящую из трех частей, разделенных точками: `header.payload.signature`.

**Пример (Base64):**
`eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9`.`eyJzdWIiOiJpdmFuQG1haWwucnUiLCJyb2xlcyI6WyJNQU5BR0VSIl0sImV4cCI6MTUxNjIzOTAyMn0`.`SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c`

**Разбор внутренностей (JSON):**

*   **1. Header (Заголовок)** — описывает алгоритм, который как раз используется для создания подписи и проверки на сервере:
    {
    "alg": "HS256",
    "typ": "JWT"
    }

*   **2. Payload (Полезная нагрузка / Claims)** — данные, которым доверяет сервер:
    {
    "sub": "ivan@mail.ru",
    "roles": ["MANAGER"],   // JwtFilter извлекает права
    "exp": 1516239022        // Срок годности токена
    }

*   **3. Signature (Подпись)** — гарантия безопасности:
    Это хеш, вычисленный по формуле: `HMACSHA256(Header + Payload + Секретный_Ключ)`.
    Если злоумышленник изменит в Payload роль с "MANAGER" на "ADMIN", подпись перестанет совпадать с той, которую вычислит сервер своим ключом, и доступ будет закрыт.


### 5. Настройка доступа и роли
Чтобы `FilterSecurityInterceptor` определял доступ пользователей, необходимо указать правила фильтрации:
1. **Глобальный (HttpSecurity):** Настраивается в центральном конфиг-классе. Удобно для закрытия целых разделов API.
   * *Пример:* `.requestMatchers(HttpMethod.POST, "/orders").hasRole("BUYER")`
2. **Точечный (Method Security):** С помощью аннотации **@PreAuthorize**. Позволяет проверять доступ прямо над методом контроллера.
   * *Пример:* `@PreAuthorize("hasRole('MANAGER')")`

### 6. Ошибки и статусы доступа
* **401 Unauthorized:** Ошибка аутентификации (Я не знаю, кто ты. Предъяви паспорт/токен).
* **403 Forbidden:** Ошибка авторизации (Я знаю, кто ты, но твоя роль не позволяет войти сюда).


### 7. Защита CSRF (Cross-Site Request Forgery)
*   **В Stateful:** Существует проблема CSRF-атак. Злоумышленник может отправить запрос со своего сайта, а браузер автоматически подставит знакомый **JSESSIONID** (куку). Защита CSRF накладывает "цифровую подпись" на формы, гарантируя, что запрос пришел с вашего сайта.
*   **В Stateless:** Мы не используем сессии и формы. JWT-токен не подставляется браузером автоматически (он добавляется фронтендом вручную). Если токен в запросе совпадает с тем, что генерируется на сервере при помощи секретного ключа — запрос считается легитимным.

---

## Пагинация в Spring Data

Когда записей в таблице становится слишком много (например, 1 000 000), обычный `findAll()` положит память приложения и фронтенд. Решение — встроенная пагинация Spring Data.

### 1. Как это выглядит на низком уровне (Spring Data JPA)

В Spring Data не нужно писать SQL вручную. Достаточно передать специальный интерфейс в метод репозитория.

**Репозиторий:**
Всё, что нужно — наследоваться от `JpaRepository` и добавить `Pageable` в параметры метода.
```java
public interface OrderRepository extends JpaRepository<Order, Long> {
// Spring сам сгенерирует SQL с LIMIT и OFFSET
Page<Order> findAll(Pageable pageable);
}
```

**Контроллер:**
Спринг автоматически парсит URL-параметры (например, `?page=0&size=20&sort=id,desc`) и упаковывает их в объект `Pageable`.

```java
@GetMapping
public Page<OrderResponse> findAll(Pageable pageable) {
// Контроллер пробрасывает настройки пагинации в сервис и далее в БД
return orderService.findAll(pageable);
}
```

### 2. Примеры SQL, которые генерирует Hibernate

Если ты запросил первую страницу (`page=0, size=10`), Spring выполнит:
```
SELECT * FROM orders ORDER BY id DESC LIMIT 10 OFFSET 0;
```

Если ты запросил 100-ю страницу (`page=100, size=10`):
```
SELECT * FROM orders ORDER BY id DESC LIMIT 10 OFFSET 1000;
```

**Важный нюанс (SELECT COUNT):**
Если возвращаемый тип метода — **Page**, Spring всегда делает второй запрос:
```
SELECT COUNT(*) FROM orders;
```
Это нужно, чтобы фронтенд знал, сколько всего страниц существует. Если общее число страниц не нужно (бесконечный скролл), лучше использовать тип **Slice** — он не делает `COUNT` и работает быстрее.

### 3. Проблема производительности (Deep Pagination)
База данных не умеет мгновенно прыгать на нужную позицию. Чтобы пропустить `OFFSET` 1 000 000 записей, БД должна **прочитать и отсчитать** их все по порядку, потратить ресурсы CPU и диска, а затем просто их отбросить.

**Решение для Highload:**
Использовать **Keyset Pagination** (или "No Offset"). Вместо номера страницы мы передаем ID последнего увиденного элемента:
```
SELECT * FROM orders WHERE id < :last_id ORDER BY id DESC LIMIT 10;
```
Так база мгновенно находит нужную запись через индекс.

### 4. Основные классы:
* **Pageable**: Интерфейс с параметрами (номер страницы, размер, сортировка).
* **Page<T>**: Объект с данными и метаинформацией (всего элементов, всего страниц).
* **Slice<T>**: Облегченный вариант (знает только, есть ли следующая страница).

---

## Динамическая фильтрация

Когда фильтров много и они могут комбинироваться в любом порядке, обычные методы репозитория не подходят. Решение — **Specification API**.

### 1. Как это работает?
Мы описываем каждое условие (предикат) как отдельный кирпичик, а затем соединяем их через `and()` или `or()`. Если значение фильтра — `null`, мы просто возвращаем пустой предикат, и Spring Data **игнорирует** его при генерации SQL.

**Пример репозитория:**
Для поддержки спецификаций репозиторий должен наследоваться от `JpaSpecificationExecutor`.

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
}
```

**Пример спецификации (логика фильтра):**

```java
public Page<UserReadDto> findAll(UserFilter filter, Pageable pageable) {
// Root - (FROM User), CQ - запрос, CB - строитель условий (CriteriaBuilder)
Specification<User> specification = (root, cq, cb) -> {
    
    // 1. Path - это путь к вложенному объекту. Заходим в таблицу PersonalInfo через User
    Path<PersonalInfo> personalInfo = root.get(User_.PERSONAL_INFO);
    
    // 2. CPredicate.builder() - кастомная обертка (помощник), которая проверяет поля на null.
    // Если filter.getName() вернет null, условие не добавится в итоговый массив.
    Predicate[] predicates = CPredicate.builder()
            
            // 3. cb.like - создаем условие "LIKE %текст%".
            // cb.lower - приводим всё к нижнему регистру для регистронезависимого поиска.
            .add(filter.getName(), (param) -> cb.like(cb.lower(personalInfo.get(PersonalInfo_.NAME)), "%" + param.toLowerCase() + "%"))
            
            .add(filter.getSurname(), (param) -> cb.like(cb.lower(personalInfo.get(PersonalInfo_.SURNAME)), "%" + param.toLowerCase() + "%"))
            
            // 4. cb.lessThan - поиск тех, кто родился РАНЬШЕ указанной даты (меньше чем параметр)
            .add(filter.getBirthDate(), (param) -> cb.lessThan(personalInfo.get(PersonalInfo_.BIRTH_DATE), param))
            
            .build();
            
    // 5. cb.and - соединяем все условия через оператор AND
    return cb.and(predicates);
};

// 6. Вызываем репозиторий. Он делает SQL с пагинацией и фильтрами одновременно.
// .map(...) - мапим Entity в DTO, чтобы не отдавать лишние данные наружу.
return userRepository.findAll(specification, pageable)
        .map(userReadMapper::map);
```

### 2. Преимущества Specification:
* **Динамичность:** Запрос формируется только из тех полей, которые заполнил пользователь.
* **Типобезопасность:** Ошибки в названиях полей видны на этапе компиляции (если использовать метамодели).
* **Чистый код:** Избавляет от огромных конструкций `if-else` при сборке строки запроса вручную.

### 3. Архитектура EAV (Entity-Attribute-Value)

#### Проблема (Логическое исключение в EAV):
У нас была задача фильтрации товаров по динамическим характеристикам. Так как каждое свойство товара — это отдельная строка в связанной таблице, возникло логическое противоречие.

Обычный **JOIN + WHERE** приводил к тому, что запросы возвращали пустые результаты при попытке выбрать два разных свойства одновременно (например: «Всего конфорок: 2» И «Ширина: 25»). Условие WHERE (свойство='конфорки' AND значение='2') AND (свойство='ширина' AND значение='25') всегда будет ложным, так как одна конкретная строка не может содержать два разных значения сразу.

#### Решение (Реляционное деление / Группировка):
Я реализовал кастомный репозиторий на **Criteria API**, используя стратегию «схлопывания» через агрегацию:
1. Вместо исключающего **AND** в блоке **WHERE** использовал оператор **OR**, чтобы собрать все потенциально подходящие строки свойств.
2. Сгруппировал результат по **product_id** (GROUP BY).
3. Добавил фильтрацию через **HAVING COUNT**, где количество найденных строк должно быть строго равно количеству активных фильтров.

#### Результат:
Это позволило системе динамически обрабатывать любое количество фильтров без изменения структуры БД. Запрос возвращает только те товары, которые обладают **полным набором** искомых характеристик одновременно. Решение масштабируемо и избавляет от «комбинаторного взрыва» методов в репозиториях.

#### Альтернативы
Для задач с огромным количеством динамических свойств (маркетплейсы) часто используют **NoSQL (MongoDB, Elasticsearch)**, так как они не имеют жесткой схемы. Однако в рамках существующей реляционной БД использование **EAV** — это стандартное индустриальное решение.

#### Что такое EAV-модель?
**Entity-Attribute-Value** — это способ хранения данных, где характеристики объекта вынесены в отдельную таблицу «ключ-значение».
* **Entity** — это продукт (Продукт 1).
* **Attribute** — название свойства (ID=10, «Всего конфорок»).
* **Value** — значение («2»).
  Это позволяет добавлять любые свойства любым товарам «на лету» без изменения структуры таблиц (без ALTER TABLE).

#### Как применить?

Представим, что от клиента приходит **ProductFilter** с динамическим набором фильтров в мапе:
```java

public class ProductFilter {
private BigDecimal priceFrom;
private BigDecimal priceBy;
private List<Long> produceIds;
// Мапа: ID свойства -> Список выбранных ID значений
private Map<Integer, List<Long>> propertyIdStringClassifierIds = new HashMap<>();
}
```
Для реализации сложной логики построения запроса можно использовать **Criteria API** напрямую:

```java

// Динамическая сборка предикатов через кастомный билдер
public Page<Product> findAllDistinctByProductFilter(ProductFilter productFilter, Long categoryId, Pageable pageable) {
    log.info("find all distinct by productfilter, filter {}", productFilter);

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    List<Product> content = getContent(productFilter, categoryId, cb, pageable);
    Long count = getCount(productFilter, categoryId, cb);

    return PageableExecutionUtils.getPage(content, pageable, () -> count);
}

private List<Product> getContent(ProductFilter productFilter, Long categoryId, CriteriaBuilder cb, Pageable pageable) {
    CriteriaQuery<Product> cq = cb.createQuery(Product.class);
    Root<Product> root = cq.from(Product.class);
    extracted(productFilter, categoryId, root, cq.select(root), cb);
    TypedQuery<Product> query = entityManager.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());
    return query.getResultList();
}

private <T> void extracted(ProductFilter productFilter, Long categoryId, Root<Product> product, AbstractQuery<T> select, CriteriaBuilder cb) {

    // 1. ПРЕДИКАТЫ ПО ТОВАРУ (Базовые: Цена, Категория, Бренд)
    // Эти поля лежат в самой таблице Product, тут всё просто (cb.between, cb.equal)
    Predicate[] productPredicates = CPredicate.builder()
            .add(filter.getPriceFrom(), filter.getPriceBy(), (p1, p2) -> cb.between(product.get(Product_.PRICE), p1, p2))
            .build();

    // 2. ПРЕДИКАТЫ ПО СВОЙСТВАМ (Динамические: Цвет, Материал и т.д.)
    // Мы итерируемся по Map, где ключ - ID свойства, а значение - список выбранных ID значений
    Predicate[] propertyPredicates = CPredicate.builder()
            .add(filter.getPropertyIdStringClassifierIds(), (values, key) ->
                    cb.and(
                            cb.equal(property.get(Property_.ID), key), // Свойство №5 (например, Цвет)
                            stringClassifier.get(StringClassifier_.ID).in(values) // Значения (Синий, Красный)
                    )
            ).build();

    // 3. СБОРКА ЗАПРОСА
    select.where(cb.and(productPredicates)) // Базовый фильтр (Цена и т.д.)
            .groupBy(product.get(Product_.ID)); // Группируем, чтобы посчитать совпадения свойств

    // 4. ГЛАВНАЯ ФИШКА: Фильтрация по количеству совпадений
    if (propertyPredicates.length != 0) {
        // Мы ищем через OR (синий ИЛИ 42), но в HAVING проверяем, 
        // чтобы количество найденных строк было РАВНО количеству фильтров.
        // Это гарантирует, что товар обладает ВСЕМИ выбранными характеристиками одновременно.
        select.having(cb.equal(cb.count(product.get(Product_.id)), propertyPredicates.length));
    }
}
```

#### Алгоритм работы фильтрации (логика "схлопывания"):

Например нужно найти продукты со свойствами: Конфорки = 2 **OR** Ширина = 25

1.  **Выгрузка:** Выгружает продукты и все связанные с ними строки свойств.
    *   Продукт 1: свойство «Конфорки», значение «2»
    *   Продукт 1: свойство «Ширина», значение «25»
    *   Продукт 2: свойство «Конфорки», значение «2»
    *   Продукт 2: свойство «Ширина», значение «50»

2.  **Фильтрация (WHERE + OR):** Оставляем только те строки, которые соответствуют хотя бы одному из выбранных фильтров (Конфорки = 2 **OR** Ширина = 25).
    *   Продукт 1: свойство «Конфорки» (значение 2) — **подходит**
    *   Продукт 1: свойство «Ширина» (значение 25) — **подходит**
    *   Продукт 2: свойство «Конфорки» (значение 2) — **подходит**
    *   Продукт 2: свойство «Ширина» (значение 50) — **не подходит**

3.  **Группировка:** Группируем результат по ID продукта и подсчитываем количество совпавших свойств:
    *   Продукт 1: **count = 2**
    *   Продукт 2: **count = 1**

4.  **Результат (HAVING COUNT):** Сравниваем количество найденных свойств с количеством активных предикатов в запросе. Если мы искали по двум фильтрам, то остаются только те товары, у которых `count = 2`.
    *   В итоге остается: **Продукт 1**.

Итоговый sql:
```sql
SELECT product_id
FROM product_properties pp
JOIN property p ON pp.property_id = p.id
WHERE (p.name = 'Конфорки' AND p.val = 2) 
   OR (p.name = 'Ширина' AND p.val BETWEEN 20 AND 27)
GROUP BY product_id
HAVING COUNT(pp.id) = 2; -- Количество совпавших условий должно быть равно числу фильтров
```

Чтобы не писать сотни блоков `if (param != null)`, можно использовать обертку над Criteria API. Она автоматически проверяет входящие параметры и игнорирует пустые фильтры.

```java
public class CPredicate {
private final List<Predicate> predicates = new ArrayList<>();

    // Если параметр не null — добавляем условие в список. 
    // Если null — просто игнорируем, и фильтр не применится.
    public <T> CPredicate add(T param, Function<T, Predicate> function) {
        if (param != null) {
            predicates.add(function.apply(param));
        }
        return this;
    }

    // Метод для работы с Map (для случая с динамическими свойствами)
    public <Y, T> CPredicate add(Map<Y, List<T>> map, BiFunction<List<T>, Y, Predicate> function) {
        if (isEmpty(map)) return this;
        for (Map.Entry<Y, List<T>> entry : map.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                predicates.add(function.apply(entry.getValue(), entry.getKey()));
            }
        }
        return this;
    }
}
```




