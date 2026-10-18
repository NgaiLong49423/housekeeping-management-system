import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit, one-time local demo setup. Not part of application startup or Flyway migrations. */
public class SeedLocalData {
    record Account(String role, String email, String password) { }
    static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public static void main(String[] args) throws Exception {
        Path runtime = Path.of(args[0]).toAbsolutePath().normalize();
        Path credentials = runtime.resolve("LOCAL-ACCOUNTS.md");
        if (Files.exists(credentials)) throw new IllegalStateException("Local account file already exists; no accounts were changed.");
        Properties config = new Properties();
        try (var reader = Files.newBufferedReader(runtime.resolve("application-local.properties"), StandardCharsets.UTF_8)) { config.load(reader); }
        Account admin = account("Admin", "admin@gonnha.local");
        Account customer = account("Customer", "customer@gonnha.local");
        Account staff = account("Staff", "staff@gonnha.local");
        List<Account> accounts = List.of(admin, customer, staff);
        try (Connection connection = DriverManager.getConnection(config.getProperty("spring.datasource.url"),
                config.getProperty("spring.datasource.username"), config.getProperty("spring.datasource.password"))) {
            if (!"hsf302_housekeeping_local".equals(connection.getCatalog())) throw new IllegalStateException("Seeder is restricted to hsf302_housekeeping_local.");
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement(); var rs = statement.executeQuery(
                        "SELECT (SELECT COUNT(*) FROM Admin)+(SELECT COUNT(*) FROM Customer)+(SELECT COUNT(*) FROM Staff)")) {
                    rs.next();
                    if (rs.getInt(1) != 0) throw new IllegalStateException("Accounts already exist; refusing to alter an existing dataset.");
                }
                long adminId = insertAccount(connection, "Admin", "Quản trị Demo", admin);
                insertAccount(connection, "Customer", "Khách hàng Demo", customer);
                long staffId = insertAccount(connection, "Staff", "Nhân viên Demo", staff);
                service(connection, "Dọn dẹp nhà theo giờ", "Làm sạch phòng khách, phòng ngủ và bếp. Dịch vụ mẫu cho môi trường local.", 120, 140000);
                service(connection, "Tổng vệ sinh nhà cửa", "Vệ sinh kỹ các khu vực trong nhà. Dịch vụ mẫu cho môi trường local.", 180, 350000);
                service(connection, "Vệ sinh sofa", "Chăm sóc khu vực sinh hoạt của gia đình. Dịch vụ mẫu cho môi trường local.", 60, 200000);
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(7)).withNano(0);
                try (var statement = connection.prepareStatement("INSERT INTO Work_Schedule(staff_id,managed_by_admin_id,available_start,available_end) VALUES(?,?,?,?)")) {
                    statement.setLong(1, staffId); statement.setLong(2, adminId);
                    statement.setObject(3, now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.ofHours(7)));
                    statement.setObject(4, now.plusDays(14)); statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        StringBuilder markdown = new StringBuilder("# Tài khoản local của project\n\nChỉ dùng cho database hsf302_housekeeping_local trên máy này. Không commit hoặc chia sẻ file này.\n\n");
        markdown.append("Đăng nhập: http://localhost:8080/login\n\n| Vai trò | Email | Mật khẩu |\n|---|---|---|\n");
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]\n");
        for (Account account : accounts) {
            markdown.append("| ").append(account.role()).append(" | ").append(account.email()).append(" | ").append(account.password()).append(" |\n");
            json.add("  {\"role\":\"" + account.role() + "\",\"email\":\"" + account.email() + "\",\"password\":\"" + account.password() + "\"}");
        }
        Files.writeString(credentials, markdown, StandardCharsets.UTF_8);
        Files.writeString(runtime.resolve("demo-accounts.json"), json.toString(), StandardCharsets.UTF_8);
        System.out.println("Created 3 local demo accounts, 3 services and a 14-day availability period. Credentials saved locally; no passwords printed.");
    }

    static Account account(String role, String email) {
        byte[] random = new byte[15]; new SecureRandom().nextBytes(random);
        return new Account(role, email, Base64.getUrlEncoder().withoutPadding().encodeToString(random) + "aA1!");
    }

    static long insertAccount(Connection connection, String table, String name, Account account) throws SQLException {
        // Table names are fixed application constants, never command-line input.
        try (var statement = connection.prepareStatement("INSERT INTO " + table + "(display_name,email,password_hash) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name); statement.setString(2, account.email());
            statement.setString(3, "{bcrypt}" + ENCODER.encode(account.password())); statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    static void service(Connection connection, String name, String description, int minutes, int price) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO Services(service_name,description,duration_minutes,base_price) VALUES(?,?,?,?)")) {
            statement.setString(1, name); statement.setString(2, description); statement.setInt(3, minutes);
            statement.setInt(4, price); statement.executeUpdate();
        }
    }
}
