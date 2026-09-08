# Spring Boot từ nền tảng đến production

> Tài liệu học theo `room-booking-backend`, dành cho người đã biết Java cơ bản và muốn hiểu Spring Boot từ cách container tạo object đến cách vận hành API ngoài production.

## Mục tiêu và cách học

Sau khi học xong, bạn cần giải thích được không chỉ “annotation dùng để làm gì” mà cả ai đọc annotation, đọc lúc nào, request chạy qua đâu, proxy ảnh hưởng ra sao, transaction bảo vệ invariant nào và hệ thống được kiểm thử/quan sát/phục hồi thế nào.

Nên học mỗi chương theo bốn bước: đọc mental model, mở file được dẫn, chạy hoặc viết test nhỏ, rồi tự trả lời phần câu hỏi. **Trong project** mô tả code đang tồn tại. **Ví dụ mở rộng** chỉ là thiết kế học tập, chưa được thêm vào build.

Project hiện dùng Java 25, Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JDBC, PostgreSQL và Liquibase. Project **không dùng JPA/Hibernate**. Chương 8 vẫn dạy JPA/Hibernate và chỉ rõ điểm khác với Spring Data JDBC để tránh trộn hai mô hình.

```text
Client
  -> Servlet/Security Filter Chain
  -> DispatcherServlet -> Interceptor -> Controller
  -> Service proxy (transaction/security/cache/async)
  -> Service -> Repository -> JDBC hoặc JPA/Hibernate -> PostgreSQL
```

IoC container tạo và nối toàn bộ graph trên. Spring Boot đọc configuration, chọn auto-configuration, dựng context và embedded server.

---

## 1. Spring Core

### IoC và Dependency Injection

Trong Java thuần, class có thể tự tạo dependency:

```java
class AuthenticationService {
    private final UserRepository repository = new JdbcUserRepository();
}
```

Class vừa chứa business logic vừa chọn implementation và cách khởi tạo, nên coupling chặt và khó test. Với Inversion of Control, quyền tạo/nối object được chuyển sang container; Dependency Injection là cách hiện thực:

```java
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}
```

Ba kiểu injection:

- constructor: dependency bắt buộc, field `final`, object hợp lệ ngay khi tạo, dễ unit test; nên là mặc định;
- setter: phù hợp dependency thực sự tùy chọn/thay đổi được;
- field: ngắn nhưng che dependency, khó khởi tạo ngoài Spring và không dùng `final`; nên tránh.

Project dùng Lombok `@RequiredArgsConstructor` để sinh constructor cho field `final`. Lombok chỉ sinh Java code lúc compile; Spring vẫn thực hiện constructor injection. Nếu có nhiều bean cùng interface, giải quyết bằng type rõ hơn, `@Primary` hoặc `@Qualifier`.

### Spring Bean và `ApplicationContext`

Bean là object do Spring IoC container tạo, cấu hình và quản lý. Object được tạo bằng `new` trong business code không tự động là bean, vì vậy injection, lifecycle và proxy không tự áp dụng.

`BeanFactory` là container nền tảng. `ApplicationContext` mở rộng nó với event, resource, i18n, AOP integration và context dành cho web. Không nên gọi `context.getBean()` rải rác vì biến DI thành Service Locator; business code nên constructor injection.

### Bean lifecycle

```text
đọc BeanDefinition
  -> gọi constructor
  -> inject dependency/property
  -> Aware callbacks
  -> BeanPostProcessor trước initialization
  -> @PostConstruct / InitializingBean / initMethod
  -> BeanPostProcessor sau initialization (có thể tạo proxy)
  -> bean sẵn sàng
  -> context đóng
  -> @PreDestroy / DisposableBean / destroyMethod
```

`BeanPostProcessor` giải thích nhiều phần “ma thuật”: một bean reference cuối cùng có thể là proxy bao quanh target. [TimeConfig.java](../../src/main/java/dev/ngb/backend/config/TimeConfig.java) dùng `@PostConstruct` để fail startup nếu JVM không ở UTC. Tránh network call lâu trong constructor. Spring không tự gọi destruction callback cho prototype bean.

### Bean scope

| Scope | Vòng đời | Use case |
|---|---|---|
| singleton | một instance trên mỗi bean definition trong mỗi context; mặc định | service/repository stateless |
| prototype | instance mới mỗi lần container resolve | object stateful ngắn hạn, hiếm dùng |
| request | một instance mỗi HTTP request | request context/accumulator |
| session | một instance mỗi HTTP session | ứng dụng có server-side session |

Spring singleton không phải GoF singleton toàn JVM. Singleton cũng không tự thread-safe; không giữ state của request trong field mutable của `@Service`. Inject prototype trực tiếp vào singleton constructor chỉ resolve một lần; muốn instance động dùng scoped proxy hoặc `ObjectProvider`.

### Component scanning và stereotype annotations

`@SpringBootApplication` tại `dev.ngb.backend` scan package đó và mọi package con. Đặt application class quá sâu sẽ bỏ sót sibling package.

| Annotation | Vai trò |
|---|---|
| `@Component` | bean tổng quát |
| `@Service` | application/business service |
| `@Repository` | persistence adapter; có thể tham gia exception translation |
| `@Controller` | MVC controller, thường trả view |
| `@RestController` | controller + tự ghi return value vào response body |
| `@Configuration` | nguồn bean definitions |
| `@Bean` | đăng ký return value của factory method |

Project dùng `@Component` cho [JwtAuthenticationFilter.java](../../src/main/java/dev/ngb/backend/filter/JwtAuthenticationFilter.java), `@Service` cho use case, `@RestController` cho API, `@Configuration` + `@Bean` trong [SecurityConfig.java](../../src/main/java/dev/ngb/backend/config/SecurityConfig.java). Spring Data tự tạo implementation cho repository interface.

**Tự kiểm tra:** Singleton bean có đồng nghĩa singleton toàn JVM không? Vì sao object tạo bằng `new` có thể bỏ qua `@Transactional`? Prototype bean được cleanup ra sao?

---

## 2. Spring Boot Core

### `@SpringBootApplication`, auto-configuration và startup

[RoomBookingBackendApplication.java](../../src/main/java/dev/ngb/backend/RoomBookingBackendApplication.java):

```java
@SpringBootApplication
public class RoomBookingBackendApplication {
    static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RoomBookingBackendApplication.class, args);
    }
}
```

Annotation gộp vai trò class cấu hình, bật auto-configuration và component scanning. Auto-configuration là cấu hình có điều kiện: Boot xem classpath, properties, loại app và bean đã có. Ví dụ có MVC starter + servlet app + chưa có cấu hình thay thế thì Boot dựng MVC infrastructure và `DispatcherServlet`. Khi app tự khai báo bean tương ứng, auto-configuration thường “back off”. Dùng `--debug`/conditions report khi cần biết vì sao cấu hình được hoặc không được áp dụng.

Startup flow:

```text
main
  -> chuẩn bị Environment và property sources/profiles
  -> suy ra application type, tạo ApplicationContext
  -> load configuration, component scan, auto-configuration
  -> chạy factory/post-processors
  -> tạo singleton, inject, lifecycle, proxy
  -> refresh context, start embedded server
  -> runners và application-ready event
```

Liquibase, datasource hoặc singleton fail lúc refresh thì app chưa ready. Đặt UTC trước `run` là có chủ ý vì connection/bean có thể được tạo trong startup.

### Starter dependencies và embedded server

[build.gradle](../../build.gradle) có starter cho Web MVC, Security, Data JDBC, Validation, Actuator, Liquibase và Mail. Starter gom dependency tương thích; auto-configuration tạo wiring có điều kiện. Thêm starter không đồng nghĩa production design đã hoàn tất.

Executable JAR chứa application và embedded servlet server, chạy độc lập bằng `java -jar`; không cần deploy WAR vào server ngoài. “Convention over configuration” cung cấp default về file config, JSON, component scan, server, nhưng default vẫn có thể override bằng property/bean.

**Tự kiểm tra:** Starter khác auto-configuration thế nào? Vì sao `@SpringBootApplication` nên ở package gốc? Khai báo custom `PasswordEncoder` ảnh hưởng auto-config/wiring ra sao?

---

## 3. Spring MVC và REST API

### Request lifecycle và `DispatcherServlet`

Spring MVC dùng Front Controller:

```text
HTTP request -> servlet filters -> DispatcherServlet
  -> HandlerMapping tìm controller method
  -> HandlerAdapter + argument resolvers
  -> JSON binding + validation -> controller
  -> return-value handler + HttpMessageConverter
  -> JSON response
```

Exception trong controller đi qua `HandlerExceptionResolver`/`@ExceptionHandler`. Lỗi Security xảy ra trước `DispatcherServlet` cần `AuthenticationEntryPoint` hoặc `AccessDeniedHandler`, không tự đi vào MVC advice.

### Mapping và request data

`@RequestMapping` thường đặt prefix ở class. `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` biểu đạt HTTP verb. [UserController.java](../../src/main/java/dev/ngb/backend/controller/UserController.java) có GET `/me`, PUT `/me/password`, POST `/me/host-profile`, DELETE `/me`.

- `@PathVariable`: identity nằm trong URI `/bookings/{id}`;
- `@RequestParam`: filter/pagination/query; project dùng `?email=`;
- `@RequestBody`: deserialize JSON thành DTO;
- `@RequestHeader`: protocol metadata như correlation ID;
- `@AuthenticationPrincipal`: principal do Security thiết lập.

### `ResponseEntity`, status và REST conventions

Trả DTO trực tiếp khi mặc định 200 đủ dùng. Dùng `ResponseEntity<T>` để điều khiển status/header/body. Project trả 201 khi register, 204 khi logout/change password/delete.

| Status | Ý nghĩa thường dùng |
|---:|---|
| 200 | thành công có body |
| 201 | tạo resource, nên có `Location` nếu có URI |
| 204 | thành công không body |
| 400 | JSON/parameter/validation sai |
| 401 | chưa xác thực/token không hợp lệ |
| 403 | đã xác thực nhưng thiếu quyền |
| 404 | không có resource |
| 409 | xung đột trạng thái/unique |
| 429 | rate limit, nên có `Retry-After` |
| 500 | lỗi bất ngờ, không lộ internals |

REST tốt dùng URI danh từ, GET không đổi state, PUT/DELETE có semantics idempotent, pagination có order ổn định, error có machine code, và không trả persistence entity trực tiếp. Payment/booking POST nên nhận idempotency key nếu retry có thể tạo side effect trùng.

**Tự kiểm tra:** 401 khác 403 thế nào? Vì sao lỗi security cần handler riêng? Khi nào DTO trực tiếp tốt hơn `ResponseEntity`?

---

## 4. Kiến trúc ứng dụng

```text
Controller: HTTP contract, binding, boundary validation, status
  -> Service: use case, business invariant, transaction boundary
    -> Repository: persistence query/contract
      -> Database: constraint, index, atomicity, durability
```

- DTO là contract request/response, không nên đồng nhất với entity.
- Entity/aggregate biểu diễn persistence/domain state.
- Mapper đổi Entity ↔ DTO; có thể là method/factory/component.
- Config tạo infrastructure bean/policy cross-cutting.
- Exception diễn đạt failure; mapping sang HTTP tại boundary.

Luồng đăng ký thật:

```text
AuthController.registerUser
 -> @Valid RegisterRequest
 -> AuthenticationService.registerUser (@Transactional)
 -> normalize + PasswordPolicy
 -> UserRepository.existsByEmail
 -> UserRegistrationFactory -> save user -> grant role -> issue tokens
 -> AuthResponse -> 201 JSON
```

Controller mỏng; service điều phối use case; repository không chứa HTTP concerns. Database unique constraint vẫn là bảo vệ cuối cùng. Hai request đồng thời có thể cùng pass `existsByEmail`; service bắt `DataIntegrityViolationException` từ unique constraint và đổi thành `EmailAlreadyRegisteredException`.

`RegisterRequest` là record immutable có validation. `User` là mutable persistence model có auditing/version. `UserResponse` không lộ `passwordHash`. Request, response và entity đổi vì ba lý do khác nhau nên không dùng chung một class.

**Tự kiểm tra:** Vì sao vẫn cần unique constraint sau existence check? Transaction boundary nên ở controller hay service? Trả entity trực tiếp có rủi ro gì?

---

## 5. Configuration

### File, value binding và nguồn cấu hình

Project dùng [application.properties](../../src/main/resources/application.properties):

```properties
spring.application.name=room-booking-backend
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
security.jwt.access-token-expiration=15m
```

YAML chỉ là biểu diễn phân cấp khác. Nên chọn một format nhất quán; nếu `.properties` và YAML cùng location thì phải hiểu precedence.

`@Value` hợp vài giá trị độc lập; [AuthEmailNotifier.java](../../src/main/java/dev/ngb/backend/service/auth/AuthEmailNotifier.java) inject URL. Nhóm cấu hình nên dùng type-safe binding:

```java
@ConfigurationProperties("security.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration) {
}
```

Đăng ký bằng configuration-properties scan/enable rồi constructor-inject record. Lợi ích: bind `Duration`, validation startup, IDE metadata, test dễ và không rải string key.

Environment variable thường map dấu chấm thành underscore và uppercase, như `SPRING_DATASOURCE_URL`. System property có dạng `-Duser.timezone=UTC`; command line có `--spring.profiles.active=local`. Không log toàn bộ environment vì có secret.

Quy tắc mental model: nguồn precedence cao override thấp; command-line/test/system/environment có thể đè file; file ngoài JAR đè file trong JAR; profile-specific đè file chung ở cùng location. Thứ tự đầy đủ có nhiều trường hợp đặc biệt, nên debug bằng tài liệu đúng version và Actuator `env` đã bảo vệ.

### Profiles

[application-local.properties](../../src/main/resources/application-local.properties) cung cấp PostgreSQL, Mailpit và development JWT secret. Chạy:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

`local/dev` dành phát triển; `test` deterministic; `staging` gần production; `prod` lấy secret từ platform. Profile phù hợp wiring/config môi trường, không nên giấu các nhánh business behavior khó audit. Production secret không có default yếu và không commit vào repo.

**Tự kiểm tra:** Khi nào `@ConfigurationProperties` hơn `@Value`? File profile quan hệ với file chung thế nào? Vì sao không dùng fallback secret ở production?

---

## 6. Validation

`@Valid` ở controller yêu cầu validate DTO sau JSON binding, trước khi method chạy. [RegisterRequest.java](../../src/main/java/dev/ngb/backend/dto/RegisterRequest.java) dùng `@NotBlank`, `@Email`, `@Size`.

| Constraint | `null` | chuỗi rỗng | chỉ whitespace | collection rỗng |
|---|---:|---:|---:|---:|
| `@NotNull` | fail | pass | pass | pass |
| `@NotEmpty` | fail | fail | pass với String | fail |
| `@NotBlank` | fail | fail | fail | không áp dụng collection |

`@Size` giới hạn string/collection/map/array; `@Min`/`@Max` cho số; `@Email` nên đi cùng required constraint; `@Pattern` dùng regex. Nhiều constraint cho phép null nên cần kết hợp `@NotNull` khi bắt buộc.

`@Validated` của Spring hỗ trợ validation groups và method validation. Nested object cần `@Valid` trên field/component để cascade.

Phân lớp rule:

- boundary/local: required, length, syntax → Bean Validation;
- business: password mới khác cũ, checkout sau checkin → service hoặc class-level validator;
- concurrent/global: email unique, availability → database constraint/lock + service error mapping.

Custom validator cho khoảng ngày:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidStayValidator.class)
public @interface ValidStay {
    String message() default "checkOut must be after checkIn";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public final class ValidStayValidator
        implements ConstraintValidator<ValidStay, CreateBookingRequest> {
    @Override
    public boolean isValid(CreateBookingRequest value, ConstraintValidatorContext context) {
        if (value == null || value.checkIn() == null || value.checkOut() == null) {
            return true;
        }
        return value.checkOut().isAfter(value.checkIn());
    }
}
```

Field constraint xử lý null; class-level validator xử lý tương quan. Không query database trong validator để “bảo đảm unique”: đó là hidden I/O và vẫn có race.

**Tự kiểm tra:** `@NotEmpty` khác `@NotBlank` thế nào? `@Valid` chạy lúc nào? Vì sao availability không thể chỉ dựa validator?

---

## 7. Exception Handling

Project có `DomainException` và các base type cho bad request, unauthorized, forbidden, not found, conflict, too many requests. Concrete exception mang stable code, message và safe data. Service không phụ thuộc `HttpStatus`; advice mapping semantics domain sang HTTP.

`@ExceptionHandler` bắt type cụ thể. Trong controller thì local; trong `@ControllerAdvice` thì cross-controller. `@RestControllerAdvice` thêm response-body serialization.

[ApiExceptionHandler.java](../../src/main/java/dev/ngb/backend/filter/ApiExceptionHandler.java) xử lý:

- domain failure → status theo base exception;
- malformed JSON/missing parameter/Bean Validation → 400 có cấu trúc;
- unexpected exception → log stack trace server-side, trả message an toàn 500.

Schema thật:

```json
{
  "timestamp": "2026-09-08T10:15:30Z",
  "status": 409,
  "code": "EMAIL_ALREADY_REGISTERED",
  "message": "email is already registered: learner@example.com",
  "data": {"email": "learner@example.com"},
  "path": "/api/v1/auth/register"
}
```

Client branch theo `status`/`code`, không exact message. Không trả SQL, stack trace, class name, raw token hoặc secret. Không catch `Exception` ở mọi controller và không trả mọi lỗi dưới HTTP 200. Security filter failure được xử lý qua entry point/access-denied handler riêng.

**Tự kiểm tra:** Vì sao domain exception không cần biết HTTP? Lỗi filter được map ở đâu? Response 500 nên chứa gì?

---

## 8. Data access: JDBC, Spring Data JDBC, JPA và Hibernate

Đây là chương cần đọc theo từng lớp. `Spring Data JDBC` không nằm “trên Hibernate”, còn `Spring Data JPA` không trực tiếp nói chuyện với JDBC driver. Hai nhánh dùng chung database và Spring transaction abstraction nhưng có runtime model khác nhau.

### 8.1 Bản đồ các tầng bên dưới

```text
Nhánh SQL/JDBC của project

Room-booking service/repository
  -> Spring Data JDBC repository / JdbcAggregateTemplate
  -> Spring JDBC: JdbcTemplate, JdbcClient, exception translation
  -> DataSource + HikariCP connection pool
  -> PostgreSQL JDBC driver
  -> PostgreSQL wire protocol
  -> PostgreSQL

Nhánh ORM/JPA phổ biến

Application
  -> Spring Data JPA repository
  -> JPA API: EntityManager, annotations, persistence context
  -> JPA provider: Hibernate ORM
  -> DataSource + HikariCP
  -> JDBC driver
  -> Database
```

Ý nghĩa từng tên:

- **JDBC** là Java API chuẩn để lấy connection, tạo statement, bind parameter, đọc `ResultSet`, commit/rollback.
- **JDBC driver** là implementation theo database, chuyển JDBC calls thành PostgreSQL protocol.
- **Spring JDBC** gồm `JdbcTemplate`, `NamedParameterJdbcTemplate`, `JdbcClient`, quản lý boilerplate resource và đổi `SQLException` thành `DataAccessException`.
- **Spring Data JDBC** thêm relational mapping, aggregate persistence, repository/query-method abstraction trên Spring JDBC.
- **JPA/Jakarta Persistence** là specification cho ORM: annotations, `EntityManager`, persistence context, entity lifecycle và query language JPQL.
- **Hibernate ORM** là một JPA provider phổ biến, hiện thực persistence context, dirty checking, lazy proxy, SQL generation và caching.
- **Spring Data JPA** tạo repository abstraction trên JPA/Hibernate, không thay thế Hibernate.

Project dùng `spring-boot-starter-data-jdbc`, không có `spring-boot-starter-data-jpa`. [User.java](../../src/main/java/dev/ngb/backend/model/User.java) import `org.springframework.data.annotation.Id` và `org.springframework.data.relational.core.mapping.Table`, không import `jakarta.persistence.Entity`. Vì vậy runtime hiện tại không có Hibernate Session, JPA persistence context, lazy proxy hoặc JPQL.

### 8.2 Raw JDBC và Spring JDBC

Raw JDBC buộc code tự quản lý `Connection`, `PreparedStatement`, `ResultSet`, close resource và `SQLException`. Mẫu an toàn phải dùng try-with-resources và bind parameter; nếu lặp ở mọi DAO sẽ nhiều boilerplate.

`JdbcTemplate` giữ quyền kiểm soát SQL nhưng xử lý vòng đời resource:

```java
@Repository
public class BookingReportRepository {
    private final JdbcClient jdbcClient;

    public BookingReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<BookingSummary> findConfirmedByGuest(UUID guestId) {
        return jdbcClient.sql("""
                SELECT id, listing_id, check_in_date, check_out_date
                FROM bookings
                WHERE guest_id = :guestId
                  AND status = 'CONFIRMED'
                ORDER BY check_in_date, id
                """)
                .param("guestId", guestId)
                .query(BookingSummary.class)
                .list();
    }
}
```

SQL vẫn do developer viết và review. Spring lấy connection phù hợp với transaction hiện tại, bind value, close resource và dịch lỗi vendor thành unchecked `DataAccessException`, ví dụ `DuplicateKeyException` hoặc `DataIntegrityViolationException`. `JdbcTemplate`/`JdbcClient` phù hợp cho report query, join/projection phức tạp, batch update hoặc SQL cần tối ưu chính xác.

Đừng lấy connection trực tiếp rồi tự commit bên trong method đang có `@Transactional`; làm vậy có thể tách operation khỏi connection mà Spring bind với transaction.

### 8.3 Mental model của Spring Data JDBC: aggregate, không phải object graph tùy ý

Spring Data JDBC lấy các khái niệm repository, aggregate và aggregate root từ Domain-Driven Design:

- aggregate là nhóm object phải nhất quán trong một atomic change;
- aggregate root là entry point duy nhất để thay đổi nhóm đó;
- thường có một repository cho mỗi aggregate root;
- object được root sở hữu có lifecycle đi cùng root;
- tham chiếu sang aggregate khác chỉ nên là ID hoặc `AggregateReference`, không phải object graph để framework cascade xuyên toàn database.

Ví dụ aggregate hợp lý:

```text
Booking (aggregate root)
  |- BookingGuestSnapshot (value object)
  `- BookingLine/BookedNight (owned children)

Booking -> listingId  (reference sang Listing aggregate)
Booking -> guestId    (reference sang User aggregate)
```

Nếu `Booking` chứa trực tiếp `User`, `Listing`, `Payment`, `Review` và mọi collection liên quan, Spring Data JDBC có thể hiểu tất cả là cùng aggregate. Save/delete sẽ có ownership semantics quá rộng. Giữ aggregate nhỏ, theo transaction invariant, và load projection riêng cho màn hình cần join nhiều aggregate.

Project hiện model `User`, `AuthToken`, `HostProfile`, `UserRole` bằng repository riêng. `User` không giữ collection role/token; service query các repository riêng và điều phối chúng trong một transaction. Đây là lựa chọn aggregate boundary rõ ràng thay vì giả lập JPA relationship graph.

### 8.4 Mapping trong Spring Data JDBC

Các annotation quan trọng đến từ Spring Data, không phải Jakarta Persistence:

- `@Table("users")`: map class vào table;
- `@Id`: primary key và một phần new-entity detection;
- `@Column("display_name")`: override column name;
- `@Version`: optimistic locking và hỗ trợ nhận diện entity mới;
- `@Transient`: không persist property;
- `@Embedded`: flatten value object vào columns của table;
- `@MappedCollection`: map collection/child thuộc aggregate root;
- `@CreatedDate`, `@LastModifiedDate`: auditing callbacks.

Ví dụ aggregate có child collection:

```java
@Table("booking_drafts")
public class BookingDraft {
    @Id
    private UUID id;

    private UUID guestId;

    @MappedCollection(idColumn = "booking_draft_id", keyColumn = "line_index")
    private List<BookingDraftLine> lines;

    @Version
    private Long version;
}

@Table("booking_draft_lines")
public class BookingDraftLine {
    private LocalDate stayDate;
    private long priceMinor;
}
```

`booking_draft_lines.booking_draft_id` trỏ về root. `line_index` giữ thứ tự list. Child thuộc lifecycle của draft; không tạo `BookingDraftLineRepository` để sửa child độc lập. Nếu line cần được nhiều aggregate tham chiếu và sống độc lập, nó phải là aggregate root khác.

Naming strategy mặc định chuyển camel case thành snake case trong các trường hợp thông thường, nhưng schema quan trọng nên được kiểm chứng bằng migration/test thay vì dựa vào phỏng đoán convention.

### 8.5 Mapping type và custom conversion trong project

Relational converter đổi row/column value thành Java property. Type đơn giản như `UUID`, `Instant`, `LocalDate`, enum có mapping chuẩn; database extension có thể cần converter.

[JdbcConversionConfig.java](../../src/main/java/dev/ngb/backend/config/JdbcConversionConfig.java) đăng ký `JdbcCustomConversions` để đổi PostgreSQL `PGobject` của `CITEXT` thành `String`. Không có converter này, driver-specific value có thể không map đúng vào email string.

[JdbcAuditingConfig.java](../../src/main/java/dev/ngb/backend/config/JdbcAuditingConfig.java) bật `@EnableJdbcAuditing` và cung cấp `DateTimeProvider` dùng shared `Clock`. Nhờ đó `@CreatedDate`/`@LastModifiedDate` deterministic trong test và nhất quán UTC.

Lưu ý: SQL `@Modifying` chạy trực tiếp không đi qua save lifecycle callbacks; auditing/version không tự thay đổi nếu câu SQL không update chúng. [UserRoleRepository.grantRole](../../src/main/java/dev/ngb/backend/repository/UserRoleRepository.java) vì thế nhận `createdAt` và INSERT giá trị rõ ràng.

### 8.6 `CrudRepository`, `ListCrudRepository` và `JdbcAggregateTemplate`

[UserRepository.java](../../src/main/java/dev/ngb/backend/repository/UserRepository.java) khai báo:

```java
public interface UserRepository extends ListCrudRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Spring tạo implementation lúc startup. Generic thứ nhất là aggregate root, thứ hai là ID type. Các method kế thừa gồm `save`, `saveAll`, `findById`, `findAll`, `existsById`, `count`, `deleteById`, `delete`. `ListCrudRepository` trả `List` cho multi-result thay vì `Iterable`.

`save` không chỉ có nghĩa “UPDATE”. Spring phải quyết định entity new hay existing để INSERT/UPDATE. Detection thường xét `@Version` rồi `@Id`; có thể tùy biến bằng `Persistable`. Project cấp UUID cho `User` trước save nhưng `version` là nullable: version null biểu đạt lần insert đầu, sau đó version tham gia optimistic locking. Đừng tùy tiện gán ID/version mà không hiểu `isNew`, nếu không Spring có thể UPDATE một row chưa tồn tại hoặc INSERT row đã có.

`JdbcAggregateTemplate` là API thấp hơn repository nhưng vẫn hiểu aggregate mapping. Nó hữu ích khi cần `insert`/`update` rõ ràng, criteria query hoặc persistence operation không khớp repository interface. Xuống `JdbcClient`/`JdbcTemplate` khi cần SQL/projection/batch kiểm soát hoàn toàn.

### 8.7 Derived query, SQL `@Query` và modifying query

Spring Data JDBC parse method name:

```java
Optional<User> findByEmail(String email);

List<AuthToken> findAllByUserIdAndTypeAndConsumedAtIsNull(
        UUID userId, AuthTokenType type);

List<AuthToken> findAllByUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
        UUID userId, AuthTokenType type, Instant earliestCreatedAt);
```

`findBy`, `existsBy`, `And`, `IsNull`, `GreaterThanEqual`, `OrderBy...Asc` được parse thành SQL predicate/order. Với Spring Data JDBC, derived queries chủ yếu hỗ trợ select và property trực tiếp của aggregate root; đừng kỳ vọng traversal association linh hoạt như JPA.

`@Query` trong JDBC luôn là **native SQL**; không có JPQL và không có `nativeQuery=true`:

```java
@Query("SELECT * FROM users WHERE id = :userId FOR UPDATE")
Optional<User> findByIdForUpdate(UUID userId);
```

Named parameter được bind, không nối input vào SQL. Query phải select đủ columns mà mapper/constructor cần. String-based `@Query` có giới hạn với pagination/sort tự động; query phức tạp nên viết SQL limit/order rõ hoặc custom implementation.

Projection scalar của project:

```java
@Query("SELECT role FROM user_roles WHERE user_id = :userId ORDER BY role")
List<Role> findRolesByUserId(@Param("userId") UUID userId);
```

Spring convert một column thành enum, không cần dựng `UserRole`. Write query cần `@Modifying`:

```java
@Modifying
@Query("""
        INSERT INTO user_roles (user_id, role, created_at)
        VALUES (:userId, :role, :createdAt)
        ON CONFLICT (user_id, role) DO NOTHING
        """)
int grantRole(UUID userId, String role, Instant createdAt);
```

Affected-row count là một phần semantics: `1` nghĩa insert, `0` nghĩa role đã có. Direct modifying query bỏ qua aggregate callbacks/events; developer chịu trách nhiệm auditing/version/invariant liên quan.

### 8.8 Save/update/delete aggregate và khác biệt với dirty checking

Spring Data JDBC không giữ persistence context và không theo dõi object sau khi load:

```java
@Transactional
public void changePassword(UUID userId, String encodedPassword) {
    User user = userRepository.findById(userId).orElseThrow();
    user.setPasswordHash(encodedPassword);
    userRepository.save(user);
}
```

Bỏ `save(user)` thì thay đổi Java object không tự thành SQL. Mỗi repository call thực thi persistence operation ngay trong transaction tương ứng; không có managed/detached entity hay automatic flush dirty state như Hibernate.

Khi save aggregate có owned child collections, Spring Data JDBC không có snapshot/dirty tracking chi tiết như Hibernate. Update aggregate có thể xóa rồi tạo lại child rows để phản ánh state hiện tại. Hệ quả:

- aggregate lớn làm nhiều SQL/write amplification;
- child ID/audit có thể bị ảnh hưởng nếu model không phù hợp;
- database FK từ aggregate ngoài vào child là dấu hiệu boundary sai;
- bulk patch thường phù hợp custom SQL hơn việc load/save graph lớn.

Repository `delete(root)` xử lý aggregate-owned entities theo lifecycle của root. Điều này khác database `ON DELETE CASCADE`: một bên là framework phát SQL theo mapping, một bên là database constraint action.

### 8.9 Loading, join và “N+1” trong Spring Data JDBC

Spring Data JDBC không lazy-load property bằng proxy. Sau repository call, aggregate được coi là loaded; truy cập getter không tự bắn SQL bất ngờ. Đây là ưu điểm lớn về tính dự đoán.

Tuy nhiên aggregate có collections có thể cần nhiều queries khi load. Một query lấy roots và các query lấy children vẫn có thể tạo chi phí giống N+1 ở cấp load strategy. Một số phiên bản hỗ trợ Single Query Loading với giới hạn về shape/dialect; không nên giả định mọi aggregate tự load bằng một SQL.

Khi màn hình cần join nhiều aggregate, ba lựa chọn rõ ràng:

1. query từng repository nếu tập nhỏ và latency chấp nhận được;
2. custom SQL qua `JdbcClient`/`JdbcTemplate` trả read-model DTO;
3. materialized/read model riêng cho query nặng.

Không kéo mọi relation vào aggregate chỉ để “join tiện”. Aggregate phục vụ consistency boundary; projection phục vụ read use case.

### 8.10 Optimistic locking trong Spring Data JDBC

`@Version` làm update/delete kiểm tra version mong đợi. Khái quát:

```sql
UPDATE users
SET password_hash = :newHash,
    version = version + 1
WHERE id = :id
  AND version = :expectedVersion;
```

Nếu affected rows bằng 0, một transaction khác đã sửa/xóa row; Spring ném optimistic-lock exception. [AuthToken.java](../../src/main/java/dev/ngb/backend/model/AuthToken.java) dùng version để hai consumer không cùng consume token thành công âm thầm. Retry phải reload state và chạy lại toàn business decision với giới hạn.

Direct SQL update chỉ được version-safe nếu query tự thêm predicate và increment version. Trộn repository save với SQL bỏ qua version mà không có quy ước sẽ phá optimistic-lock invariant.

### 8.11 Transaction với Spring Data JDBC

Một repository call riêng có transaction semantics của repository/framework, nhưng use case nhiều calls phải đặt boundary ở service:

```text
AuthenticationService.registerUser (@Transactional)
  -> INSERT users
  -> INSERT user_roles
  -> INSERT auth_tokens
  -> commit tất cả hoặc rollback tất cả
```

Spring transaction manager bind một JDBC connection từ `DataSource` vào thread. Repositories, `JdbcTemplate` và `JdbcClient` trong cùng thread/transaction dùng connection đó thông qua Spring infrastructure. Không có persistence context không có nghĩa “không có transaction”; ACID vẫn do database + JDBC transaction cung cấp.

Project bắt `DataIntegrityViolationException` do unique email và đổi thành domain exception. Existence check tạo message sớm nhưng unique constraint mới đóng race. Đây là ví dụ đúng về phối hợp repository abstraction với database invariant.

### 8.12 JPA là gì và Hibernate nằm ở đâu?

JPA định nghĩa contract:

- `@Entity`, `@Id`, `@OneToMany`, `@ManyToOne` và mapping annotations;
- `EntityManager` với `persist`, `find`, `merge`, `remove`, `flush`;
- persistence context và entity lifecycle;
- JPQL/Criteria API;
- cascade, fetching, locking.

JPA không tự chạy. Hibernate là provider đọc metadata JPA, tạo Session/persistence context, sinh SQL, snapshot managed entity, tạo lazy proxies/collections và tương tác JDBC. Có thể dùng Hibernate native API, nhưng trong Spring Data JPA thường code chủ yếu dựa JPA contract cộng feature Hibernate khi cần.

Spring Data JPA tiếp tục giảm boilerplate bằng `JpaRepository`, derived queries, `@Query`, specification, projection và paging. Stack vẫn là:

```text
JpaRepository -> EntityManager (JPA contract) -> Hibernate -> JDBC -> database
```

### 8.13 JPA entity, `JpaRepository`, JPQL và native SQL

Ví dụ JPA tương đương để học, không phải code project hiện tại:

```java
@Entity
@Table(name = "users")
public class JpaUser {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Version
    private long version;

    protected JpaUser() {
    }
}

public interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {
    Optional<JpaUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
```

JPQL query entity/property, không query table/column:

```java
@Query("select u from JpaUser u where lower(u.email) = lower(:email)")
Optional<JpaUser> findAccount(@Param("email") String email);
```

Native JPA query dùng SQL:

```java
@Query(value = "SELECT * FROM users WHERE id = :id FOR UPDATE",
        nativeQuery = true)
Optional<JpaUser> findByIdForUpdate(@Param("id") UUID id);
```

So sánh cú pháp: `@Query` của Spring Data JDBC mặc định đã là SQL; `@Query` của Spring Data JPA mặc định là JPQL và cần `nativeQuery=true` để dùng SQL.

### 8.14 Persistence context, entity lifecycle và dirty checking của Hibernate

```text
Transient --persist--> Managed --flush/commit--> database
Managed --detach/clear/close--> Detached
Managed --remove--> Removed
Detached --merge--> Managed copy
```

Persistence context bảo đảm identity: load cùng entity ID trong cùng context thường nhận cùng Java object. Hibernate snapshot/track managed entity. Khi property đổi, dirty checking sinh UPDATE lúc flush:

```java
@Transactional
public void rename(UUID id, String displayName) {
    JpaUser user = repository.findById(id).orElseThrow();
    user.setDisplayName(displayName);
}
```

Không cần `save(user)` để dirty managed entity được flush, dù gọi save có thể giữ code theo repository abstraction. `flush` đẩy SQL xuống database nhưng chưa phải commit; transaction vẫn rollback được. Detached object không còn được dirty-check; `merge` trả về managed copy, không biến chính instance truyền vào thành managed.

Spring Data JDBC không có bốn state này. Object chỉ là Java object; muốn persist thay đổi phải gọi save/update rõ ràng.

### 8.15 JPA relationship mapping, lazy/eager và N+1

- `@OneToOne`: một user–một host profile;
- `@OneToMany`: một booking–nhiều booked nights;
- `@ManyToOne`: nhiều booking tham chiếu một listing;
- `@ManyToMany`: join table; khi relation có role/createdAt, thường biến join thành entity riêng.

Owning side điều khiển foreign key/join update; `mappedBy` là inverse side. Bidirectional association phải đồng bộ cả hai phía trong domain helper nếu không in-memory graph và SQL có thể lệch.

Lazy association dùng Hibernate proxy/persistent collection và chỉ load khi truy cập trong active context. Truy cập sau context đóng có thể gây `LazyInitializationException`. Eager yêu cầu association được load nhưng không cam kết chỉ một SQL.

Query 100 bookings rồi truy cập `booking.getListing()` có thể tạo 1 query bookings + 100 query listings: N+1. Giải pháp: JPQL fetch join, entity graph, DTO projection, batch fetching và test/metrics query count. Đổi mọi mapping sang EAGER thường chỉ chuyển lỗi sang over-fetch hoặc vẫn có nhiều SQL.

Fetch join collection cùng pagination có thể duplicate root hoặc paginate sai/in-memory. Thường page root IDs trước rồi fetch graph, hoặc dùng DTO projection được thiết kế cho page.

### 8.16 Cascade và orphan removal trong Hibernate/JPA

Cascade truyền entity operation từ parent sang association:

- `PERSIST`: persist parent thì persist child;
- `MERGE`: merge parent thì merge child;
- `REMOVE`: remove parent thì remove child;
- `REFRESH`, `DETACH`: truyền operation tương ứng;
- `ALL`: gộp các operation.

`orphanRemoval=true` xóa child khi child bị bỏ khỏi owned association. Cascade không đồng nghĩa database `ON DELETE CASCADE`; Hibernate cascade tạo entity operations/SQL, database cascade chạy trong constraint engine. Không dùng `CascadeType.REMOVE` tùy tiện trên many-to-many vì related entity có thể được aggregate khác dùng chung.

Spring Data JDBC không có `CascadeType` annotation. Ownership được suy từ aggregate object graph: save/delete root xử lý owned children theo mapping. Cross-aggregate reference cần được quản lý riêng.

### 8.17 Bảng so sánh lựa chọn

| Tiêu chí | Spring JDBC | Spring Data JDBC | JPA/Hibernate + Spring Data JPA |
|---|---|---|---|
| SQL | tự viết | generated CRUD + derived/simple SQL; custom SQL trực tiếp | ORM sinh SQL; JPQL/Criteria/native SQL |
| Mapping | RowMapper/manual | convention + relational annotations | rich ORM annotations |
| Unit of persistence | query/update | aggregate root | managed entity graph/persistence context |
| Dirty checking | không | không | có với managed entity |
| Lazy loading | không | không | có proxy/collection |
| Relationship | SQL/join thủ công | owned aggregate + ID/reference | one-to-one/many-to-one/collections/bidirectional |
| N+1 risk | do query design | aggregate child loading/repository loops | lazy/eager graph traversal |
| SQL predictability | cao nhất | cao | cần hiểu provider và inspect SQL |
| Portability dialect | thấp hơn | có dialect nhưng SQL custom phụ thuộc DB | JPQL portable hơn; provider/database vẫn khác |
| Phù hợp | report, batch, SQL tối ưu | aggregate vừa/nhỏ, model rõ, muốn explicit | domain graph phức tạp, ORM features cần thiết |

Không có lựa chọn thắng tuyệt đối:

- chọn Spring Data JDBC khi muốn SQL/lifecycle dễ dự đoán, aggregate boundary rõ, không cần lazy/dirty checking;
- chọn JPA/Hibernate khi relationship graph, unit-of-work, dirty checking và ORM tooling đem lại lợi ích đủ lớn;
- dùng JdbcTemplate/JdbcClient cùng Spring Data JDBC/JPA cho read query đặc thù là bình thường;
- không chọn JPA chỉ để có `JpaRepository`, và không chọn JDBC chỉ vì sợ học Hibernate; chọn theo write model, query patterns, team expertise và performance evidence.

### 8.18 Project walkthrough và checklist debug

Đọc theo thứ tự:

1. [build.gradle](../../build.gradle): xác nhận starter JDBC, PostgreSQL driver, Liquibase.
2. [User.java](../../src/main/java/dev/ngb/backend/model/User.java): `@Table`, `@Id`, auditing, `@Version`.
3. [UserRepository.java](../../src/main/java/dev/ngb/backend/repository/UserRepository.java): inherited CRUD, derived query, explicit row-lock SQL.
4. [UserRoleRepository.java](../../src/main/java/dev/ngb/backend/repository/UserRoleRepository.java): scalar projection và `@Modifying` PostgreSQL upsert.
5. [JdbcConversionConfig.java](../../src/main/java/dev/ngb/backend/config/JdbcConversionConfig.java): vendor type conversion.
6. [JdbcAuditingConfig.java](../../src/main/java/dev/ngb/backend/config/JdbcAuditingConfig.java): callbacks lấy thời gian từ shared clock.
7. [AuthenticationService.java](../../src/main/java/dev/ngb/backend/service/auth/AuthenticationService.java): service transaction nối nhiều repositories.
8. [001-identity.sql](../../src/main/resources/db/changelog/changes/001-identity.sql): schema constraints là nguồn bảo vệ cuối.

Khi persistence sai, kiểm tra theo thứ tự: transaction có thật sự mở không; SQL/parameters là gì; entity được coi new hay existing; column naming/type conversion đúng không; version predicate có bị bypass không; query có load quá nhiều round trips không; constraint/index có khớp invariant/query không.

**Tự kiểm tra:**

1. Spring Data JDBC có đi qua Hibernate không?
2. Spring Data JPA, JPA và Hibernate mỗi tầng chịu trách nhiệm gì?
3. Vì sao `User` có ID trước save vẫn có thể được nhận là entity mới nhờ nullable version?
4. Tại sao sửa object đã load nhưng không gọi `save` sẽ không update trong Spring Data JDBC?
5. `@Query` của Data JDBC khác `@Query` của Data JPA thế nào?
6. Vì sao modifying SQL phải tự xử lý auditing/version?
7. Aggregate-owned child khác cross-aggregate reference ra sao?
8. Không có lazy loading có loại bỏ mọi dạng N+1/round-trip thừa không?
9. Hibernate `flush` khác transaction `commit` thế nào?
10. Khi nào projection bằng `JdbcClient` tốt hơn mở rộng aggregate graph?

---

## 9. Transaction

Transaction gom nhiều database operation thành một unit: commit tất cả hoặc rollback. Boundary hợp lý thường là service use case. `HostOnboardingService` cần tạo profile và grant role atomically; `AuthenticationService.registerUser` cần user, role và token nhất quán.

```text
caller -> transactional proxy -> begin/join
       -> target method -> commit khi thành công
                        -> rollback theo rule khi lỗi
```

Không giữ transaction mở khi gọi HTTP/SMTP lâu vì connection/lock bị giữ và remote side effect không rollback cùng DB. Project xử lý email bằng `@TransactionalEventListener(AFTER_COMMIT)`: không gửi cho transaction rollback, nhưng SMTP fail sau commit vẫn làm token tồn tại; production cần outbox/retry nếu notification không được phép mất.

### Propagation

| Mode | Hành vi |
|---|---|
| `REQUIRED` | join transaction hiện có, nếu không tạo mới; mặc định |
| `REQUIRES_NEW` | suspend transaction cũ, tạo transaction độc lập |
| `SUPPORTS` | có thì join, không có vẫn chạy |
| `MANDATORY` | fail nếu caller chưa có transaction |
| `NOT_SUPPORTED` | suspend và chạy không transaction |
| `NEVER` | fail nếu đang có transaction |
| `NESTED` | savepoint nếu manager hỗ trợ |

`REQUIRES_NEW` có thể cần connection khác và làm cạn pool; chỉ dùng khi semantics thật sự độc lập.

### Isolation, read-only và rollback

Các anomaly: dirty read, non-repeatable read, phantom, lost update/write skew. Isolation thường tăng từ `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ` đến `SERIALIZABLE`, nhưng behavior phụ thuộc database MVCC. PostgreSQL mặc định READ COMMITTED. Isolation cao không thay unique constraint hoặc lock cho invariant cụ thể.

`@Transactional(readOnly=true)` là hint/optimization và documentation, không phải security guarantee cấm write trên mọi stack. Project dùng nó cho query methods trong `UserAccountService`.

Mặc định Spring rollback với `RuntimeException`/`Error`, không rollback checked exception. Khi checked exception cần rollback:

```java
@Transactional(rollbackFor = BookingImportException.class)
public void importBookings(Path file) throws BookingImportException {
    bookingImporter.importFile(file);
}
```

Đừng catch exception rồi im lặng nếu cần rollback.

### Self-invocation

Method `outer()` gọi `this.inner()` trực tiếp trên target không qua proxy, nên annotation `@Transactional` trên `inner()` không được intercept. Cách rõ nhất là tách `inner` sang bean có trách nhiệm riêng và inject bean đó. Self-inject hoặc lấy proxy từ context làm code khó hiểu.

**Tự kiểm tra:** Gửi email trong DB transaction nguy hiểm gì? `readOnly` có cấm UPDATE chắc chắn? Vì sao self-invocation bỏ qua advice?

---

## 10. Spring AOP và Proxy

Proxy có cùng contract với target, chặn method call để thêm hành vi trước/sau/quanh lời gọi. Caller giữ proxy; proxy delegate target. Đây là nền tảng cho transaction, cache, async và method security.

- JDK dynamic proxy proxy interface.
- CGLIB tạo subclass concrete class; không advise được `final`, `private` hoặc method không thể override.
- Runtime class có thể là proxy; không viết logic dựa `getClass() == X.class`.
- Lời gọi phải đi qua bean reference của container; `new`, self-invocation, private method không đi qua proxy-based advice.

Thuật ngữ: join point là điểm có thể chặn; pointcut chọn method; advice là hành vi; aspect gộp pointcut + advice. Ví dụ mở rộng:

```java
@Aspect
@Component
public class ServiceTimingAspect {
    private static final Logger log = LoggerFactory.getLogger(ServiceTimingAspect.class);

    @Around("execution(public * dev.ngb.backend.service..*(..))")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMicros = (System.nanoTime() - startedAt) / 1_000;
            log.debug("method={} elapsedMicros={}", joinPoint.getSignature(), elapsedMicros);
        }
    }
}
```

Aspect phải gọi `proceed()` đúng semantics. Không log raw arguments vì có password/token/PII. Production latency nên dùng Micrometer Observation. Khi một method có nhiều advice, ordering ảnh hưởng kết quả: authorization/cache/transaction/retry bao nhau theo thứ tự nào cần được thiết kế và test.

Các feature proxy:

- `@Transactional`: transaction interceptor;
- `@Async`: submit invocation sang executor;
- `@Cacheable`: lookup cache, có thể bỏ qua target;
- `@PreAuthorize`: evaluate quyền trước target.

**Tự kiểm tra:** Vì sao final method khó với CGLIB? `@Transactional` trên private method có hiệu lực không? `@Around` quên `proceed()` gây gì?

---

## 11. Spring Security

### Authentication, Authorization và SecurityContext

Authentication trả lời “ai đang gọi?”, authorization trả lời “người đó được làm gì?”. `Authentication` chứa principal, credentials và authorities; `SecurityContext` chứa Authentication; `SecurityContextHolder` cho code trong request thread truy cập context. Principal đã xác thực vẫn nhận 403 nếu thiếu quyền; chưa xác thực nhận 401.

[SecurityConfig.java](../../src/main/java/dev/ngb/backend/config/SecurityConfig.java) tạo stateless filter chain:

- tắt form login và HTTP Basic;
- tắt server session bằng `STATELESS`;
- public auth, email-exists, health và OpenAPI routes;
- `/api/v1/admin/**` cần ADMIN;
- route còn lại cần authentication;
- JWT filter chạy trước username/password filter.

[JwtAuthenticationFilter.java](../../src/main/java/dev/ngb/backend/filter/JwtAuthenticationFilter.java) đọc Bearer token, verify claims, tạo `UsernamePasswordAuthenticationToken`, đổi roles thành authorities có `ROLE_` prefix rồi đặt vào context. Invalid token để request anonymous; authorization sau đó gọi [RestAuthenticationEntryPoint.java](../../src/main/java/dev/ngb/backend/config/RestAuthenticationEntryPoint.java) trả JSON 401.

### User, Role, Authority và method security

Role là nhóm quyền như `ADMIN`, `HOST`; authority có thể chi tiết như `booking:approve`. `hasRole("ADMIN")` kiểm tra `ROLE_ADMIN`. Role không đủ cho ownership/state: host chỉ sửa listing của chính mình. Policy đó cần service/domain check hoặc method authorization.

`@PreAuthorize` kiểm tra trước method; `@PostAuthorize` sau khi có result. Với write, ưu tiên kiểm tra trước để không tạo side effect. Cần bật method security và nhớ self-invocation/proxy caveat.

```java
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
public UserResponse getUser(UUID userId) {
    return userAccountService.getUser(userId);
}
```

### Password, JWT, OAuth2/OIDC

Không hash password bằng SHA-256 thuần. Dùng adaptive one-way `PasswordEncoder`. Project tạo delegating encoder có `{id}` algorithm prefix, so bằng `matches(raw, encoded)`. Không log raw password/hash. Login dùng cùng lỗi cho email lạ và password sai để giảm account enumeration.

JWT được ký nhưng payload không được mã hóa; không đặt secret/PII nhạy cảm vào claims. Project dùng access JWT 15 phút và refresh token opaque 30 ngày; refresh token chỉ lưu hash và được rotate/consume. Cần validate signature, allowed algorithm, expiry, issuer/audience khi dùng, key rotation và clock skew. Stateless logout không vô hiệu access token ngay nếu không có denylist/token version, nên access token cần ngắn hạn.

OAuth 2.0 là authorization framework; OpenID Connect thêm identity layer và ID Token. Backend resource server verify Access Token với issuer/JWK. Web/mobile public client thường dùng Authorization Code + PKCE. Không dùng ID Token thay Access Token một cách tùy tiện và không tự viết authorization server nếu không có lý do mạnh.

### CORS và CSRF

CORS là browser cross-origin policy, không chặn curl/server-to-server và không phải authorization. Chỉ allow origin/method/header cần thiết; tránh wildcard với credentials.

CSRF lợi dụng credential browser tự gửi như cookie session. Bearer token trong `Authorization` header không được browser tự gắn, nên stateless API thường có thể tắt CSRF như project. Nếu chuyển token sang cookie/session, phải đánh giá lại; “REST luôn tắt CSRF” là sai.

**Tự kiểm tra:** JWT ký có che payload không? CORS có bảo vệ API khỏi curl không? Role khác ownership policy ra sao? SecurityContext đi qua thread async tự động không?

---

## 12. Filter, Interceptor và AOP

```text
HTTP request
 -> Servlet Filter / Spring Security FilterChainProxy
 -> DispatcherServlet
 -> HandlerInterceptor.preHandle
 -> Controller
 -> proxied Service / AOP
 -> Interceptor completion
 -> Filter response path
```

| Công cụ | Nhìn thấy | Use case |
|---|---|---|
| Servlet Filter | raw request/response, trước MVC | JWT, CORS, wrapping, correlation ID |
| MVC Interceptor | handler/controller metadata | locale, MVC audit/timing |
| AOP | Spring bean method/arguments | transaction, method security, cache, service metrics |

Security filter chain được bridge vào servlet chain; thứ tự filter quan trọng. `OncePerRequestFilter` hỗ trợ một lần mỗi request dispatch theo contract. Interceptor không thay thế security filter vì không chắc bao mọi servlet/resource. AOP không tự biết HTTP khi call đến từ scheduler/message consumer.

Project JWT auth là filter vì identity phải có trước controller. `ApiExceptionHandler` nằm trong package `filter` nhưng về kỹ thuật là MVC advice, không phải servlet filter.

Correlation filter mẫu:

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = supplied == null || supplied.isBlank()
                ? UUID.randomUUID().toString() : supplied;
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", id)) {
            response.setHeader(HEADER, id);
            chain.doFilter(request, response);
        }
    }
}
```

Production giới hạn length/charset của ID client gửi để tránh log injection/cardinality. Luôn cleanup MDC vì servlet thread được tái sử dụng.

**Tự kiểm tra:** Vì sao JWT nằm ở filter? Interceptor biết gì mà filter không biết? Vì sao MDC phải cleanup?

---

## 13. Testing

### Test pyramid và test slices

- JUnit 5 điều khiển test; AssertJ cung cấp assertion; Mockito mock collaborator.
- Unit test không Spring context: nhanh, kiểm một class/business rule.
- Slice test chỉ load phần framework: MVC hoặc persistence.
- Integration test nối nhiều layer và infrastructure thật.
- End-to-end gọi deployment qua network.

`@SpringBootTest` load full context, dùng cho wiring/cross-layer chứ không mặc định mọi test. `@WebMvcTest` load MVC slice và dùng MockMvc; mock service, cấu hình security test có chủ ý. `@DataJpaTest` là JPA slice và không đúng với project JDBC hiện tại; dùng JDBC slice phù hợp version hoặc integration test datasource thật.

Project có unit tests cho time, token và JDBC conversion. [TimeConfigTest.java](../../src/test/java/dev/ngb/backend/config/TimeConfigTest.java) gọi config trực tiếp, nhanh và deterministic.

### Cần test gì theo layer

Service registration:

- happy path save user/grant GUEST/issue token;
- duplicate pre-check không write;
- database unique race đổi thành conflict;
- password policy fail trước persistence;
- integration test lỗi giữa hai write rollback toàn bộ.

Controller test chỉ contract: route, status, JSON, validation, authorization và error shape. Ví dụ MockMvc:

```java
mockMvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {
                  "email": "learner@example.com",
                  "password": "StrongPass1!",
                  "displayName": "Learner"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.email").value("learner@example.com"));
```

Repository test phải chạy PostgreSQL thật để bắt `CITEXT`, `TIMESTAMPTZ`, locks, constraint và dialect. Testcontainers tạo DB disposable và Boot có thể nối config qua service connection tùy setup. Test derived queries, unique/FK/check, optimistic conflict, `FOR UPDATE`, Liquibase-from-zero và time round-trip. Concurrency test cần hai connection/transaction độc lập; một test transaction chung không tái hiện race.

**Tự kiểm tra:** Mock repository che bug gì? Vì sao H2 không đủ cho project? `@SpringBootTest` có phải unit test không?

---

## 14. Logging

SLF4J là facade; Logback là implementation thường dùng. Lombok `@Slf4j` sinh logger. Dùng parameterized logging, ví dụ `log.info("bookingId={} status={}", id, status)`, thay vì nối chuỗi.

| Level | Dùng cho |
|---|---|
| TRACE | chi tiết cực cao khi debug hẹp |
| DEBUG | dữ liệu chẩn đoán developer |
| INFO | lifecycle/business event đáng chú ý |
| WARN | bất thường có thể phục hồi/cần chú ý |
| ERROR | operation thất bại cần điều tra |

Không log một exception ở mọi layer; chọn boundary có đủ context và log một lần. Không log password, Authorization header, JWT, refresh/reset token, payment data. Structured log production nên có timestamp, level, service, environment, event, correlation/trace ID, latency và outcome dưới dạng key-value/JSON.

MDC là thread-local context. Filter thêm correlation ID và xóa trong `finally`; nếu không, thread pool tái sử dụng thread khiến metadata request cũ rò sang request mới. `@Async`, reactive và messaging cần propagate context có chủ ý. Correlation ID nối log; trace ID nối distributed spans. Không dùng userId/bookingId làm metrics tag cardinality cao.

**Tự kiểm tra:** Vì sao không log JWT? MDC rò giữa request thế nào? Khi nào WARN hơn ERROR?

---

## 15. Actuator và Monitoring

Project có Actuator starter và public `/actuator/health`. Actuator cung cấp health, metrics, info, loggers và endpoint khác tùy enable/expose. Không public tất cả: `env`, config, heap dump có thể lộ bí mật.

Phân biệt:

- liveness: process có kẹt đến mức phải restart không;
- readiness: instance có nhận traffic được không;
- dependency health: DB/mail/storage đang ra sao.

Liveness không nên fail chỉ vì database tạm down, nếu không mọi pod restart làm sự cố nặng hơn. Readiness có thể loại instance khỏi load balancer khi dependency bắt buộc chưa sẵn sàng.

Micrometer là metrics facade. Counter đếm event; gauge đo hiện tại; timer đo count/latency; distribution summary đo kích thước. Metric booking nên tag outcome/market cardinality thấp; không tag userId/email/bookingId vì số time-series bùng nổ.

Prometheus scrape metrics; Grafana query/dashboard/alert. Dashboard nên có request rate, error, p95/p99 duration, saturation, Hikari pool, executor queue và consumer lag. Alert theo symptom/SLO, không alert mọi log ERROR.

Distributed tracing nối spans qua HTTP, DB và message; context phải propagate qua headers/message metadata. Sampling kiểm soát chi phí; không đưa PII/secret vào span attributes.

```text
Metrics: có vấn đề không?
Traces: request hỏng/chậm ở hop nào?
Logs: sự kiện và stack trace cụ thể là gì?
```

**Tự kiểm tra:** Vì sao DB down không nên luôn làm liveness fail? Metrics cardinality cao gây gì? Endpoint Actuator nào cần bảo vệ?

---

## 16. Cache

Cache-aside:

```text
read -> hit: return
     -> miss: DB -> cache với TTL -> return
write -> commit DB -> invalidate/update cache
```

Cache là bản sao có thể stale, không phải source of truth. Trước khi cache phải xác định key, TTL, invalidation, consistency, failure behavior và memory bound.

- `@Cacheable`: hit thì bỏ qua method; miss gọi và lưu.
- `@CachePut`: luôn gọi method rồi cập nhật cache.
- `@CacheEvict`: xóa key hoặc cache region.

```java
@Cacheable(cacheNames = "userProfiles", key = "#userId",
        unless = "#result == null")
public UserResponse getUser(UUID userId) {
    return loadUser(userId);
}

@CacheEvict(cacheNames = "userProfiles", key = "#userId")
@Transactional
public void changeDisplayName(UUID userId, String displayName) {
    updateDisplayName(userId, displayName);
}
```

Cần `@EnableCaching` và cache manager. Annotation dựa proxy nên self-invocation vẫn lỗi. Phải phối hợp transaction: update cache trước commit có thể lộ dữ liệu chưa commit; evict after commit qua event/outbox thường rõ hơn.

Redis chia sẻ cache giữa instances. TTL giới hạn stale/memory nhưng không thay invalidation. Thêm jitter để tránh nhiều key hết hạn đồng thời. Failure modes: penetration (key không tồn tại), stampede (hot key expire), avalanche (nhiều key expire), stale authorization, serialization incompatibility. Dùng negative caching ngắn, single-flight/locking, stale-while-revalidate hoặc refresh-ahead tùy use case.

Availability/payment phải xác nhận bằng DB/transaction dù cache nói hợp lệ.

**Tự kiểm tra:** `@Cacheable` khác `@CachePut` ở target invocation? TTL có thay invalidation không? Vì sao cache role/status lâu nguy hiểm?

---

## 17. Async và Scheduling

`@Async` submit invocation sang `Executor`. Cần bật async và cấu hình bounded pool/queue/rejection/name:

```java
@Bean("notificationExecutor")
ThreadPoolTaskExecutor notificationExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("notification-");
    executor.initialize();
    return executor;
}

@Async("notificationExecutor")
public CompletableFuture<Void> sendBookingConfirmation(UUID bookingId) {
    emailSender.sendBookingConfirmation(bookingId);
    return CompletableFuture.completedFuture(null);
}
```

Async không durable: process chết trước task thì mất việc. Nghiệp vụ bắt buộc dùng outbox + broker/job store. Transaction, MDC và SecurityContext không tự truyền qua mọi executor. `CompletableFuture` mang lỗi về caller; `void` cần `AsyncUncaughtExceptionHandler`. Self-invocation không kích hoạt async proxy.

Scheduling:

```java
@Scheduled(cron = "0 */5 * * * *", zone = "UTC")
public void expireStaleReservations() {
    reservationExpiryService.expireBatch();
}
```

Spring cron thường có sáu field gồm giây; chỉ định zone. Trên ba pod, job có thể chạy ba lần, nên cần idempotency, distributed lock hoặc DB claim. Xử lý batch giới hạn, checkpoint, metrics; không load toàn bảng. Theo dõi active threads, queue, rejection, latency; graceful shutdown ngừng nhận task rồi drain trong timeout.

**Tự kiểm tra:** `@Async` có chống mất task khi crash? Cron trên nhiều pod xảy ra gì? Queue không giới hạn nguy hiểm ra sao?

---

## 18. Messaging

Kafka là distributed append log, mạnh về throughput, partitions, retention/replay. RabbitMQ là broker theo queues/exchanges/routing, hợp work queue/routing patterns. Chọn theo semantics.

Producer serialize/send; consumer deserialize/process rồi ack/commit offset. Network failure tạo kết quả không chắc chắn: producer timeout không chứng minh broker chưa nhận; consumer xử lý xong nhưng crash trước ack sẽ nhận lại. Thiết kế mặc định nên giả định at-least-once và consumer idempotent.

### Kafka core

- topic chia partitions; ordering chỉ trong một partition;
- stable key như `bookingId` giữ event cùng aggregate vào một partition;
- trong consumer group, một partition do tối đa một consumer xử lý tại một thời điểm;
- offset là vị trí đọc; commit sau xử lý giảm mất message nhưng vẫn duplicate;
- partition count giới hạn parallelism hữu ích của group.

Retry chỉ cho lỗi tạm thời; dùng exponential backoff + jitter + max attempts. Permanent validation/schema/business error đi **Dead Letter Queue (DLQ)** sớm. Retry topic có thể phá ordering. DLQ cần alert, owner, retention, inspect/redrive tool và duplicate protection.

Idempotency strategies: bảng `processed_messages` có unique `(consumer,message_id)` cùng transaction với business write; natural unique constraint; upsert/state transition; API idempotency key; producer outbox.

```text
DB transaction: update booking + insert outbox row
 -> publisher claim/publish/mark sent
 -> consumer deduplicate + apply business write
```

Outbox tránh dual-write “DB commit nhưng broker publish fail”. Exactly-once end-to-end qua DB, broker, email/payment không đến từ một flag; cần idempotency và reconciliation từng boundary.

Event nên là fact `BookingConfirmed`, có eventId, aggregateId, occurredAt, schemaVersion và payload tối thiểu. Không publish raw entity serialization. Schema evolution nên additive và có contract tests.

**Tự kiểm tra:** Kafka ordering có phạm vi nào? Vì sao commit offset sau xử lý vẫn duplicate? Outbox giải quyết dual-write thế nào?

---

## 19. Database thực tế

### Pool, index và pagination

HikariCP tái sử dụng connection. Pool lớn không luôn nhanh: PostgreSQL có giới hạn và quá nhiều connection tăng contention. Theo dõi active/idle/pending/acquisition timeout; transaction dài giữ connection lâu. Project chạy `SET TIME ZONE 'UTC'` cho mỗi connection qua `connection-init-sql`.

Index tăng tốc read nhưng tốn storage/write. Thiết kế từ query pattern; composite index theo equality/range/order và left-prefix; partial index cho subset; unique index bảo vệ invariant. Dùng `EXPLAIN (ANALYZE, BUFFERS)` trên dữ liệu gần thật. Unique email trong migration là correctness trước performance.

Offset pagination đơn giản nhưng page sâu đắt và trôi khi concurrent insert. Keyset pagination dùng sort key ổn định:

```sql
SELECT * FROM bookings
WHERE (created_at, id) < (:createdAt, :id)
ORDER BY created_at DESC, id DESC
LIMIT :limit;
```

Luôn có tie-breaker unique, max page size và index khớp predicate/order.

### Locking

Optimistic locking dùng version; UPDATE có `WHERE id=? AND version=?`, zero row nghĩa conflict. [User.java](../../src/main/java/dev/ngb/backend/model/User.java) và `AuthToken` có `@Version`. Phù hợp conflict hiếm; retry toàn use case với state mới và giới hạn, không chỉ retry save state cũ.

Pessimistic lock dùng `SELECT ... FOR UPDATE`, như `UserRepository.findByIdForUpdate`. Lock giữ đến transaction end, có blocking/deadlock; giữ critical section ngắn, lock order nhất quán, timeout/monitor. Java `synchronized` không bảo vệ nhiều pod.

### Liquibase/Flyway và migration

Project dùng Liquibase formatted SQL, master changelog theo thứ tự. Migration đã chạy ở shared environment phải immutable; thêm forward changeset. Không dùng ORM auto-DDL tự sửa production.

Rolling deployment dùng expand–migrate–contract khi đổi schema:

1. thêm column mới nullable;
2. app ghi cả cũ/mới;
3. backfill theo batch;
4. app đọc mới;
5. thêm constraint/index;
6. release sau mới xóa cũ.

Test migration từ zero và upgrade data thật; đánh giá lock duration, backup/restore. Booking availability cần database invariant/atomic update/lock/exclusion constraint, không chỉ “SELECT thấy trống rồi INSERT”, vì hai transaction có thể cùng thấy trống.

**Tự kiểm tra:** Vì sao pool lớn có thể chậm hơn? Optimistic conflict retry ở mức nào? Offset page sâu kém ra sao? Expand–contract giúp rolling deploy thế nào?

---

## 20. Production và Deployment

### Build và runtime

Spring Boot hỗ trợ cả Maven và Gradle. Maven mô tả lifecycle/dependency bằng `pom.xml` và thường chạy `./mvnw test`, `./mvnw package`; Gradle dùng DSL trong `build.gradle` và task graph. Project này chọn Gradle, vì vậy phải dùng wrapper đã commit thay vì phụ thuộc Gradle cài toàn máy. Hai build tool không thay đổi runtime model của Spring; khác nhau chủ yếu ở build model, plugin và cú pháp dependency.

```bash
./gradlew test
./gradlew build
java -Duser.timezone=UTC -jar build/libs/room-booking-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

Gradle wrapper pin tool; build artifact một lần rồi promote qua environments. Docker image nên multi-stage/buildpack, non-root, pin base, không bake secret, giới hạn resources và chỉ chứa artifact cần. File [compose.local.yaml](../../compose.local.yaml) dùng **Docker Compose** để dựng PostgreSQL/PostGIS, MinIO, Mailpit cho local; Compose không tự cung cấp HA/autoscaling/secret management.

Inject DB/JWT/mail config từ platform config/secret manager, validate lúc startup và lên kế hoạch rotate. JWT rotation cần nhiều verification keys/`kid` trong transition. Không dùng local credential ngoài production.

### Kubernetes cơ bản

- Deployment: replicas và rolling update;
- Service: discovery/load balancing;
- ConfigMap/Secret: config; Secret vẫn cần encryption/RBAC;
- startup/readiness/liveness probes;
- resource requests/limits;
- HPA theo metrics phù hợp;
- PodDisruptionBudget cho maintenance.

Tổng DB connections = pool mỗi pod × số pod; autoscale app không tính DB sẽ quá tải DB.

### Graceful shutdown

```text
SIGTERM
 -> instance not-ready/ngừng traffic mới
 -> hoàn tất request trong timeout
 -> scheduler/consumer ngừng lấy việc
 -> executor drain có giới hạn
 -> đóng context/datasource/server
 -> exit
```

Platform termination grace phải dài hơn app shutdown + load-balancer delay. Client vẫn cần idempotency vì server có thể commit nhưng response bị mất.

Production checklist:

- migration test với kích thước gần thật; backup/restore đã diễn tập;
- secret/config bắt buộc validate; least privilege;
- timeout budget cho HTTP, DB, downstream; retry không khuếch đại tải;
- logs redacted có correlation/trace ID;
- RED metrics, pool, queue/lag có dashboard/alert;
- health probes đúng semantics;
- graceful shutdown, rollback release, load/capacity test;
- dependency/image security scan.

**Tự kiểm tra:** Vì sao build once/promote? Scale pod ảnh hưởng DB pool ra sao? Graceful shutdown vẫn cần idempotency vì sao?

---

## 21. Đi xuyên request thật: `POST /api/v1/auth/register`

1. Embedded server tạo servlet request/response.
2. Security filter chain chạy; register được `permitAll`.
3. `DispatcherServlet` chọn `AuthController.registerUser`.
4. JSON converter tạo `RegisterRequest`; `@Valid` chạy. Fail thì controller không được gọi và advice trả 400.
5. Controller gọi `AuthenticationService` qua transactional proxy.
6. Proxy mở transaction; service normalize, validate password, check email, tạo/save user, grant role và issue tokens.
7. Spring Data repository dùng connection/transaction bind với thread.
8. Unique race tạo `DataIntegrityViolationException`, được đổi thành domain conflict; proxy rollback; advice trả 409.
9. Thành công thì proxy commit, controller trả 201, Jackson serialize `AuthResponse`.
10. Với verification/reset flow, email listener chạy `AFTER_COMMIT`; cơ chế hiện tại chưa durable trước process crash, nên outbox là bước tiến hóa production.

| Cơ chế | Vai trò trong request |
|---|---|
| IoC/DI | nối controller, service, repository, encoder |
| Boot | MVC/JSON/datasource/server auto-config |
| MVC | route, binding, validation, response |
| Security | public/protected policy |
| AOP | transaction quanh service |
| Spring Data | repository implementation/SQL mapping |
| Database | constraints và atomic commit |
| Advice | stable error JSON |
| Actuator/logs | health và diagnosis |

---

## 22. Lộ trình thực hành

### Giai đoạn 1 — Core, Boot, MVC

1. Vẽ bean graph từ `AuthController` đến `PasswordEncoder`.
2. Đặt breakpoint ở constructor, `@PostConstruct`, controller và service.
3. Gọi register/login/email-exists; thử missing field, malformed JSON, duplicate.
4. Viết GET endpoint có path variable, query param và status đúng.

Mục tiêu: giải thích container, startup, request lifecycle và validation boundary.

### Giai đoạn 2 — Persistence và transaction

1. Map `001-identity.sql` sang `User`.
2. Bật SQL log local/test và quan sát derived query.
3. Test hai transaction consume cùng `AuthToken` để thấy optimistic lock.
4. Test hai transaction với `FOR UPDATE` để thấy blocking.
5. Ném lỗi giữa save user và grant role để xác nhận rollback.

Mục tiêu: phân biệt service check/database invariant, optimistic/pessimistic, JDBC/JPA, flush/commit.

### Giai đoạn 3 — Security và proxy

1. Trace Authorization header → SecurityContext → `@AuthenticationPrincipal`.
2. Test public, no token, bad token, GUEST và ADMIN.
3. Tạo demo self-invocation transaction.
4. Dùng `AopUtils.isAopProxy` trong test inspect service.
5. Thêm method security cho một ownership rule và test.

Mục tiêu: mô tả invocation path thay vì coi annotation tự chạy.

### Giai đoạn 4 — Production

1. Thêm structured logging/correlation ID với redaction test.
2. Prometheus metrics + RED/Hikari dashboard.
3. Testcontainers PostgreSQL cho repository.
4. Thiết kế Redis cache listing gồm key/TTL/invalidation/stampede.
5. Thiết kế booking outbox + idempotent notification consumer.
6. Containerize và test graceful shutdown khi request đang chạy.

Mục tiêu: chứng minh service observable, recoverable và chịu lỗi.

### Trọng tâm Senior

- Spring internals: bean definitions, post-processors, lifecycle, conditional auto-config, proxy boundaries/advice ordering.
- Database: execution plan, index, persistence context, isolation, locks/deadlocks, N+1, batching, pool sizing.
- Security: threat model, filter chain, JWT rotation, OAuth2/OIDC, ownership, CORS/CSRF.
- Production: SLO, RED/USE, tracing, timeout budgets, retry amplification, graceful degradation, capacity.
- Distributed systems: at-least-once, ordering, idempotency, outbox/inbox, eventual consistency, reconciliation/saga.

Câu trả lời Senior tốt luôn nêu invariant, failure modes, cách đo, recovery và giới hạn của giải pháp.

---

## 23. Câu hỏi ôn tập tổng hợp

1. `@SpringBootApplication` gộp gì và scan từ đâu?
2. Singleton bean có tự thread-safe không?
3. Bean post-processor liên quan proxy thế nào?
4. Malformed JSON khác Bean Validation failure ở bước nào?
5. Vì sao controller trả DTO thay entity?
6. Config source nào cung cấp local JWT secret?
7. `@Valid` và `@Validated` khác use case gì?
8. Vì sao advice không luôn bắt security-filter error?
9. Data JDBC khác JPA về dirty checking/lazy loading?
10. `save` có đồng nghĩa commit ngay không?
11. Checked exception mặc định rollback không?
12. Self-invocation ảnh hưởng bốn annotation proxy nào?
13. JDK proxy khác CGLIB ra sao?
14. JWT signing khác encryption?
15. Role, authority và ownership khác nhau thế nào?
16. Correlation, MVC handler audit và transaction lần lượt dùng filter/interceptor/AOP ra sao?
17. Unit, slice, integration test bắt lớp lỗi nào?
18. Vì sao user ID không là metrics tag?
19. Cache stampede là gì?
20. `@Async` khác durable messaging ở guarantee nào?
21. Consumer group/partition quyết định parallelism ra sao?
22. Vì sao consumer phải idempotent?
23. Optimistic/pessimistic lock hợp contention nào?
24. Expand–contract bảo vệ rolling deployment ra sao?
25. Graceful shutdown phối hợp readiness, server, executor và timeout thế nào?

---

## 24. Tài liệu chính thức

- [Spring Framework — IoC Container](https://docs.spring.io/spring-framework/reference/core/beans.html)
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Spring Boot — Auto-configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring MVC — DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html)
- [Spring Framework — JDBC data access](https://docs.spring.io/spring-framework/reference/data-access/jdbc.html)
- [Spring Data JDBC Reference](https://docs.spring.io/spring-data/relational/reference/jdbc.html)
- [Spring Data JDBC — Aggregates and relational databases](https://docs.spring.io/spring-data/relational/reference/jdbc/domain-driven-design.html)
- [Spring Data JDBC — Query methods](https://docs.spring.io/spring-data/relational/reference/jdbc/query-methods.html)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
- [Hibernate ORM Documentation](https://hibernate.org/orm/documentation/)
- [Spring Framework — Declarative Transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- [Spring Framework — AOP Proxying](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Security — Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [Spring Framework — Cache Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)
- [Spring Boot — Actuator Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot — Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring for Apache Kafka — Retry Topics](https://docs.spring.io/spring-kafka/reference/retrytopic.html)

Luôn đọc đúng major version mà project sử dụng. Proxy defaults, package, auto-configuration và test support có thể đổi giữa major versions.
