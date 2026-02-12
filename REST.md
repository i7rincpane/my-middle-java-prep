# REST API & Архитектура
* ### [Шесть принципов REST (Рой Филдинг)](#rest-principles-p)
* ### [Модель зрелости Ричардсона](#maturity-model-p)
* ### [Практика проектирования REST API](#design-practice-p)
* ### [Пример RESTful контроллера](#restful-controller-p)

## <a id="rest-principles-p">Шесть принципов REST (Рой Филдинг)</a>

REST API (Representational State Transfer Application Programming Interface) — это архитектурный стиль взаимодействия между клиентом и сервером через HTTP. Он определяет принципы построения API, обеспечивая стандартизированный и эффективный обмен данными между различными системами.

REST — это **передача «представления» состояния** ресурса.
* **Ресурс** — это сущность на сервере (например, User в БД).
* **Представление** — это отформатированная копия данных (JSON/XML), описывающая состояние этого ресурса, которую сервер передает клиенту.
Клиент не меняет данные в базе напрямую, он получает представление, модифицирует его и отправляет обратно.

Чтобы система считалась REST-архитектурой, она должна соответствовать 6 критериям(RESTful):

1. **Client-Server:** Разделение ответственности. Клиент не заботится о хранении данных, сервер не заботится о пользовательском интерфейсе.
2. **Stateless (Без состояния):** Сервер не хранит данные о прошлых запросах клиента. Каждый запрос должен содержать ВСЮ информацию для его выполнения (например, токен авторизации).
3. **Cacheable (Кэширование):** Ответы должны помечаться как кэшируемые или нет, чтобы клиент не запрашивал одни и те же данные дважды.
4. **Layered System (Многослойная архитектура):** Клиент не должен знать, общается он напрямую с сервером или с промежуточным узлом (балансировщиком, прокси).
5. **Code on Demand (Код по запросу):** (Необязательный) Возможность передачи исполняемого кода от сервера клиенту (например, JavaScript-скрипты).
6. **Uniform Interface (Единый интерфейс):** Главный принцип. Он требует, чтобы все ресурсы имели уникальный адрес (URI), а общение шло через стандартные методы (HTTP) и медиа-типы.

## <a id="maturity-model-p">Модель зрелости Ричардсона</a>

Эта модель показывает, насколько api соответствует идеалам REST.

### Уровень 0: "Болото POX" (Plain Old XML/JSON)
Используем HTTP просто как транспорт для передачи данных.
* **Пример:** Один эндпоинт `/api`, на который всё шлется через `POST`. Внутри JSON мы пишем, что хотим сделать. (как в soap)
* *Запрос:* `POST /api` { "action": "getUser", "id": 1 }
* *Запрос:* `POST /api` { "action": "deleteUser", "id": 1 }

### Уровень 1: Ресурсы (Resources)
Мы выделяем для каждой сущности свой адрес (**URI**). Это существительные, но мы всё еще используем их как "команды".
* **Ресурс** — это сущность (Пользователь, Пост). У каждого свой адрес.
* *Пример:* `POST /users/1` (чтобы получить), `POST /users/1/delete` (чтобы удалить).
* **Минус:** Используется один метод (обычно POST), а логика действий зашита в ссылки.

### Уровень 2: HTTP Методы и Статус-коды
Мы начинаем использовать HTTP как язык. Вместо глаголов в адресе используем HTTP-глаголы.
* **URI — только существительные:** `/users/1`
* **Методы — действия:**
    * `GET /users/1` — получить.
    * `DELETE /users/1` — удалить.
    * `POST /users` — создать (сервер вернет статус **201 Created**).
* **Статус-коды:** Если ресурса нет, сервер обязан вернуть **404 Not Found**, а не 200 OK с текстом ошибки.

### Уровень 3: HATEOAS
"Hypermedia as the Engine of Application State". Сервер возвращает не только данные, но и ссылки на действия, доступные сейчас. В идеале, клиент вообще не должен знать как строится URL на ресурсы.
* **Пример:** Ты запросил данные заказа. Сервер прислал JSON и блок `_links`:
    * `{ "id": 5, "status": "new", "_links": { "cancel": "/orders/5/cancel", "pay": "/orders/5/pay" } }`
* **Плюс:** Клиенту не нужно знать все URL заранее, он "гуляет" по API, следуя ссылкам сервера.

##  <a id="design-practice-p">Практика проектирования REST API</a>

### 1. Правила формирования URL (Naming Convention)

*   **Использование префикса и версии:** `/api/v1/` — позволяет отделять API от фронтенда и безболезненно обновлять логику.
*   **Существительные во множественном числе:** Используем `/orders` вместо `/order`. Мы работаем с коллекцией ресурсов.
*   **Создание заказа:** `POST /api/v1/orders`. Данные передаются строго в теле запроса (Request Body).

### 2. Изменение состояния: Статус (PATCH) vs Ресурс-действие (POST)

#### Вариант А: Изменение статуса (PATCH /orders/1)
Подходит, когда логика проста и сосредоточена в одном месте.
*   **Механика:** Клиент меняет поле `status`. Внутри сервиса используется `switch/case`, который при переходе в статус `SHIPPING` вызывает методы проверки склада и уведомлений.
*   **Минус:** Нарушается принцип единственной ответственности (Single Responsibility) — сервис заказа начинает знать слишком много о логистике и СМС-шлюзах.

#### Вариант Б: Ресурс-действие (POST /orders/1/shipment-actions)
Подходит для сложных систем и микросервисов. Мы создаем **событие**.
*   **Механика:** Мы создаем новую запись в таблице "Действия по отгрузке".
*   **Логика:** Сохранение этого ресурса порождает событие (Event). Другие сервисы (Доставка, Уведомления) подписываются на него и выполняют свои задачи независимо.
*   **Плюс:** Гибкость и масштабируемость. Мы можем добавить 10 новых действий после отгрузки, не меняя код основного сервиса заказов.

### 3. Частичное обновление: Проблема "null" в PATCH и решение через PUT

Главная проблема метода **PATCH** в Java — это невозможность стандартными средствами отличить `null` (желание стереть данные) от отсутствия поля в JSON (желание проигнорировать поле).

#### Вариант 1. PATCH через Map (Признак наличия поля)
Чтобы понять, прислал ли клиент поле, мы принимаем запрос в виде `Map<String, Object>`. Наличие ключа в мапе и есть тот **признак**, по которому мы решаем: обновлять поле или нет.

**Контроллер:**
@PatchMapping("/{id}")
public void patchOrder(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
orderService.applyPatch(id, updates);
}

**Сервис:**
@Transactional
public void applyPatch(Long id, Map<String, Object> updates) {
Order order = repository.findById(id).orElseThrow();

    // ПРИЗНАК: проверяем, было ли поле вообще прислано в JSON
    if (updates.containsKey("description")) {
        // Если ключ есть — обновляем поле в БД, даже если там пришел null (стирание)
        order.setDescription((String) updates.get("description"));
    }
    
    if (updates.containsKey("price")) {
        order.setPrice((Integer) updates.get("price"));
    }
    // Те поля, которых нет в Map, просто игнорируются и сохраняют старые значения
}

#### Вариант 2. Использование PUT
Это самый простой и надежный архитектурный способ. Вместо того чтобы мучиться с `if` и проверками на `null`, мы перекладываем ответственность на клиента.

**Логика:**
1. Клиент запрашивает объект целиком через `GET`.
2. На своей стороне (на фронтенде) он меняет одно нужное поле (например, ставит `null`).
3. Отправляет **весь объект целиком** обратно через `PUT`.

**Контроллер:**
@PutMapping("/{id}")
public void updateFullOrder(@PathVariable Long id, @RequestBody OrderDto dto) {
// Мы не гадаем, пришел null или нет. Мы просто перезаписываем объект.
// Если в dto поле null — значит в БД оно тоже станет null.
orderService.save(id, dto);
}

**Плюс:** Код сервиса становится чистым и предсказуемым.
**Минус:** Гоняется лишний трафик, если объект очень тяжелый.

### 4. Идемпотентность: Борьба с дубликатами при разрыве сети

**Проблема дубликатов (Почему суррогатный ID из БД не подходит):**
1. Клиент жмет «Оплатить». Запрос уходит на сервер.
2. Сервис создает запись в БД, база генерирует ID (например, 10).
3. **Сеть обрывается** до того, как сервер успел ответить «Успешно, твой ID 10».
4. Клиент не знает, что заказ создан. Он нажимает «Оплатить» еще раз.
5. Сервер получает второй запрос и создает **Заказ №11**. У клиента списали деньги дважды.

**Решение (Idempotency Key / UUID):**
Клиент генерирует **UUID** сам *до* отправки. При обрыве сети второй запрос придет с тем же UUID. Сервер проверит кэш и просто вернет данные заказа №10, не создавая новый.

```java
// Реализация на сервисе (проверка через Redis)
public OrderResponse createOrder(String uuid, OrderDto dto) {
if (cache.exists(uuid)) {
return cache.get(uuid); // Возвращаем сохраненный результат без создания в БД
}
Order order = repository.save(new Order(dto));
cache.put(uuid, order, Duration.ofHours(24));
return new OrderResponse(order);
}
```

## <a id="restful-controller-p">Пример RESTful контроллера</a>

Ниже приведен пример реализации, которая соответствует модели зрелости Ричардсона (Уровень 3) и принципам Филдинга.

```java
// Принцип Client-Server — четкое разделение ответственности через REST API
// Уровень 1 (Resources): Сущность Order выделена в отдельный ресурс /api/v1/orders
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService orderService;

  // Принцип Stateless — сервер не хранит состояние клиента между запросами
  // Уровень 2 (HTTP Verbs): Используем правильный метод GET для получения данных
  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
    OrderResponse response = orderService.findById(id);

    // Уровень 3 (HATEOAS): Добавляем ссылки на возможные действия
    // Сервер сам говорит клиенту, что делать дальше (оплатить или удалить).
    response.add(linkTo(methodOn(OrderController.class).deleteOrder(id)).withRel("delete"));
    response.add(linkTo(methodOn(OrderController.class).payOrder(id)).withRel("pay"));

    return ResponseEntity.ok(response); // Уровень 2: Правильный статус-код 200 OK
  }

  // Уровень 2: Используем POST для создания ресурса
  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
    OrderResponse created = orderService.create(request);

    // Принцип Uniform Interface — возвращаем URI созданного ресурса в заголовке Location
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();

    return ResponseEntity.created(location).body(created); // Клиент получает 201 Created и готовую ссылку на новый объект
  }

  // Ресурс-действие (POST): Перемещение заказа
  // Мы не используем глагол /moveToShipping, а создаем "факт перемещения"
  @PostMapping("/{id}/shipment-actions")
  public ResponseEntity<Void> moveOrder(@PathVariable Long id) {
    // ПРИНЦИП: Layered System (Слойная архитектура) — контроллер только 
    // дергает сервис, не зная, что там: логика склада или отправка в Kafka
    orderService.processShipment(id);
    return ResponseEntity.accepted().build(); // 202 Accepted (принято в обработку)
  }

  // PATCH обычный (через DTO)
  // Проблема: Нельзя занулить поле, так как null в DTO может означать "не прислали"
  @PatchMapping("/simple/{id}")
  public void patchOrderSimple(@PathVariable Long id, @RequestBody OrderDto dto) {
    // Здесь сработает логика: if (dto.getField() != null) { update... }
    orderService.applySimplePatch(id, dto);
  }

  // PATCH для частичного обновления (null vs undefined)
  // УРОВЕНЬ 2: Частичное обновление через PATCH
  // Принцип Uniform Interface - Взаимодействие через представления (JSON)
  @PatchMapping("/{id}")
  public ResponseEntity<Void> patchOrder(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
    // Мы используем Map как "Признак": если ключ в мапе есть — обновляем (даже в null)
    orderService.applyPatch(id, updates);
    return ResponseEntity.noContent().build(); // 204 No Content
  }

  // УРОВЕНЬ 2: Использование метода PUT для ПОЛНОГО обновления ресурса.
  // PUT — это самый надежный способ "обнулить" поля.
  @PutMapping("/{id}")
  public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id, @RequestBody OrderRequest request) {
        /*
          Вместо того чтобы писать в PATCH кучу проверок 'if (null)', 
          мы используем PUT. Клиент запрашивает объект (GET), меняет нужные поля на null 
          (стирает их) и присылает объект ЦЕЛИКОМ. 
          Сервер просто сохраняет его как есть, затирая старую версию.
        */
    OrderResponse updated = orderService.update(id, request);
    return ResponseEntity.ok(updated);
  }

  // ИДЕМПОТЕНТНАЯ ОПЛАТА (Безопасно при повторных кликах)    
  // ВАРИАНТ 1: Оплата БЕЗ явного ключа идемпотентности
  // Используется, когда клиент не может сгенерировать UUID (например, старые системы).
  @PostMapping("/simple/{id}/pay")
  public ResponseEntity<Void> payOrderSimple(@PathVariable Long id) {
    // КРИТИЧЕСКИЙ НЕДОСТАТОК: Если клиент нажмет кнопку 2 раза из-за лага сети, 
    // сервер может создать два платежа, так как у него нет уникального ID запроса.
    // Идемпотентность здесь ложится на плечи БД (например, уникальный индекс на паре order_id + status).

    orderService.processPayment(id);

    // УРОВЕНЬ 2: 202 Accepted — платеж принят в обработку, но результат будет позже.
    return ResponseEntity.accepted().build();
  }

  // ВАРИАНТ 2: ИДЕМПОТЕНТНАЯ ОПЛАТА (Middle-стандарт)
  // Гарантирует безопасность при повторных кликах или разрывах соединения.
  @PostMapping("/{id}/pay")
  public ResponseEntity<Void> payOrder(
          @PathVariable Long id,
          @RequestHeader("Idempotency-Key") String key) { // UUID, созданный на клиенте

    // ЛОГИКА: Сервер сохраняет 'key' в Redis на 24 часа. 
    // При повторном запросе с тем же ключом сервис просто вернет 200 OK, 
    // не обращаясь к логике списания денег.

    orderService.pay(id, key);

    // УРОВЕНЬ 2: 200 OK — подтверждение успешного списания.
    return ResponseEntity.ok().build();
  }

  // УРОВЕНЬ 2: Использование метода DELETE для удаления ресурса.
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
    orderService.delete(id);

    // ПРАВИЛО: Если мы успешно удалили объект и нам нечего возвращать в теле,
    // правильный статус-код — 204 No Content.
    return ResponseEntity.noContent().build();
  }
}
```


