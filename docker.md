## Multi-stage build (Многоэтапная сборка)

### Каждый FROM начинает новый этап.

* На первом этапе собирается jar и копируется все что для этого нужно(компилятор Jdk, зависимости, исходники, сборщик)
* На втором этапе, мы только забираем jar и задаем настройки для его запуска. Для запуска хватает JRE.
* *Итог:* все что было на первом этапе выбрасывается, в итоговый образ попадает только то что скопировали на втором FROM - этапе

```text
# Назвали первый этап -  build
FROM eclipse-temurin:21-jdk-alpine AS build 
WORKDIR /app

COPY gradlew .
COPY gradle gradle

COPY build.gradle .
COPY settings.gradle .

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# from=build - указываем взять файлы из предыдущего этапа
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Работа с сетью
* **Создание сети:** `docker network create user-service-network`
* **Посмотреть все сети:** `docker network ls`
* **Посмотреть подключенные контейнеры (блок "Containers": {}):** `docker network inspect user-service-network`
* **Проверить сеть через контейнер:** `docker inspect <имя_контейнера>`

* **Создать базу для сервиса, задать имя/пароль, подключить к сети, пробросить порты, чтобы обратиться с хоста:localhost:5432 (сервисы в одной сети и без проброса смогут):** `docker run -d --name user-service-db --network user-service-network -e POSTGRES_DB=user_service_db -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine`

* **Собираем проект, указываем имя (-t), текущий каталог(.):** `docker build -t user-service:0.0.1 .`

* **Запускаем контейнер, переопределяем настройки:** `docker run -d --name user-service --network user-service-network -p 8080:8080 -e SPRING_R2DBC_URL=r2dbc:postgresql://user-service-db:5432/user_service_db -e SPRING_DATASOURCE_URL=jdbc:postgresql://user-service-db:5432/user_service_db -e SPRING_R2DBC_USERNAME=postgres -e SPRING_R2DBC_PASSWORD=postgres -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=postgres user-service:0.0.1`
* **БД:** `docker run -d --name user-service-db --network user-service-network -e POSTGRES_DB=user_service_db -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine`

* **Проверить работающие:** docker ps (если с опцией -a, то все)

* **Проверить логи если не запустился:** docker logs user-service

* **Удалить контейнер:** `docker rm -f user-service`

## Testcontainers 

Каждому тесту — своя изолированная база на своем случайном порту. Они никак не пересекаются.

Если бы мы использовали одну общую базу для всех тестов (на статичном порту), возникли бы «грязные данные»:
* **Тест А** удаляет пользователя. 
* **Тест Б** в это же время пытается этого пользователя найти.
* **Результат:** Тест Б падает из-за действий Теста А.

`registry.add("spring.datasource.url", postgres::getJdbcUrl);`

Мы говорим Спрингу: «Когда тебе понадобится url, вызови getJdbcUrl().
getJdbcUrl спросит у Docker на каком порту запущена база и вернет, например, `jdbc:postgresql://localhost:32789/testdb.`


# 🚀 Шпаргалка по Docker Compose

### 🔄 Основные команды управления


| Команда | Описание | Когда применять |
| :--- | :--- | :--- |
| `docker-compose up -d` | **Старт / Обновление** | Обычный запуск. Docker сам подхватит изменения в `.yml` файле. |
| `docker-compose up -d --force-recreate` | **Пересоздание** | Если нужно гарантированно пересоздать контейнер (например, сбросить внутренние ID). |
| `docker-compose up -d --build` | **Пересборка образа** | Если ты изменил **код** микросервиса или `Dockerfile`. |
| `docker-compose restart` | **Перезапуск** | Быстрая перезагрузка процесса внутри контейнера без его пересоздания. |

### 🧹 Очистка и удаление


| Команда | Описание |
| :--- | :--- |
| `docker-compose stop` | Просто остановить сервисы (данные и контейнеры сохраняются). |
| `docker-compose down` | Удалить контейнеры и сети, созданные этим файлом. |
| `docker-compose down -v` | **Полная зачистка**: удаляет контейнеры + **Volumes** (базы данных, топики Кафки). |

### 🔍 Просмотр состояния и логов


| Команда | Описание |
| :--- | :--- |
| `docker-compose ps` | Посмотреть статус всех сервисов в текущем проекте. |
| `docker-compose logs -f` | Следить за логами всех сервисов в реальном времени. |
| `docker logs [container_name] -f` | Смотреть логи конкретного контейнера (например, `kafkaserver`). |

#### В docker logs -f (follow)

Здесь это режим "следовать за логами".

*  **Зачем:** Без этого флага Docker просто выведет текст, который уже накопился в логах, и завершит команду. С флагом -f терминал останется открытым и будет в реальном времени дописывать новые строки по мере их появления.
* `(Чтобы выйти из этого режима, нажми Ctrl + C).`

---

### 💡 Лайфхаки для работы с Kafka
*   **Сбросить все топики:** `docker-compose down -v` -> `docker-compose up -d`.
*   **Запустить только Кафку (без UI):** `docker-compose up -d zookeeper kafkaserver`.
*   **Проверить, что порт открыт:** `netstat -ano | findstr :9092` (в PowerShell).


