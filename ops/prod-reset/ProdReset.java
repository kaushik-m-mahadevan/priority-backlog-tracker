import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.nio.file.*;
import java.util.*;

/**
 * One-off: reset the prod database to a single ADMIN account (Kaushik) and nothing else.
 * Everything else (items, groups, notifications, config, counters, history, other users)
 * is deleted; `config` reseeds to defaults on the next app boot.
 *
 *   java ... ProdReset            -> read-only: prints what is in the DB now
 *   java ... ProdReset apply      -> performs the reset, then prints the result
 *
 * Connection string: env MONGODB_URI if set, else read from ../../config/dev.properties
 * (same Atlas cluster; only the database name differs). Database: env MONGO_DB or "prod".
 */
public class ProdReset {

    // bcrypt("password123"), strength 10 — matches the app's BCryptPasswordEncoder
    static final String PW_HASH = "$2a$10$MchyjqT4RvVNT73eNc66ru/gjy5yZR1AunzUQU7YHzLmwa2EsDEmu";

    static final String ADMIN_NAME   = "Kaushik";
    static final String ADMIN_EMAIL  = "kaushik.m.mahadevan@gmail.com";
    static final String ADMIN_HANDLE = "cessabit_kerl";

    public static void main(String[] args) throws Exception {
        boolean apply = args.length > 0 && args[0].equalsIgnoreCase("apply");

        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            Path p = Paths.get("..", "..", "config", "dev.properties");
            for (String line : Files.readAllLines(p)) {
                line = line.trim();
                if (line.startsWith("spring.data.mongodb.uri=")) {
                    uri = line.substring("spring.data.mongodb.uri=".length()).trim();
                }
            }
        }
        if (uri == null || uri.isBlank()) {
            System.err.println("No connection string. Set MONGODB_URI or fill config/dev.properties.");
            System.exit(2);
        }
        String dbName = System.getenv().getOrDefault("MONGO_DB", "prod");

        System.out.println("Target database : " + dbName);
        System.out.println("Mode           : " + (apply ? "APPLY (will delete)" : "inspect only"));
        System.out.println("Host           : " + uri.replaceAll("://[^@]+@", "://***@"));
        System.out.println();

        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase db = client.getDatabase(dbName);

            System.out.println("---- BEFORE ----");
            dump(db);

            if (!apply) {
                System.out.println("\nInspect only. Re-run with the argument  apply  to perform the reset.");
                return;
            }

            System.out.println("\n---- APPLYING ----");
            for (String name : db.listCollectionNames()) {
                if (name.equals("users")) continue;
                long removed = db.getCollection(name).deleteMany(new Document()).getDeletedCount();
                System.out.printf("  %-16s cleared %d docs%n", name, removed);
            }

            MongoCollection<Document> users = db.getCollection("users");
            long delUsers = users.deleteMany(new Document()).getDeletedCount();
            System.out.printf("  %-16s cleared %d docs%n", "users", delUsers);

            Document admin = new Document("_id", new ObjectId())
                    .append("_class", "com.backlogtracker.user.domain.User")
                    .append("name", ADMIN_NAME)
                    .append("email", ADMIN_EMAIL)
                    .append("passwordHash", PW_HASH)
                    .append("role", "ADMIN")
                    .append("status", "ACTIVE")
                    .append("handle", ADMIN_HANDLE)
                    .append("createdAt", new Date())
                    .append("approvedAt", new Date());
            users.insertOne(admin);
            System.out.println("  users            inserted 1 ADMIN (" + ADMIN_EMAIL + " / handle @" + ADMIN_HANDLE + ")");

            System.out.println("\n---- AFTER ----");
            dump(db);
            System.out.println("\nDone. Password is 'password123'. The app reseeds `config` defaults on next start.");
        }
    }

    static void dump(MongoDatabase db) {
        List<String> names = new ArrayList<>();
        db.listCollectionNames().into(names);
        Collections.sort(names);
        for (String name : names) {
            MongoCollection<Document> c = db.getCollection(name);
            long n = c.countDocuments();
            System.out.printf("  %-16s %d%n", name, n);
            if (name.equals("users")) {
                for (Document d : c.find()) {
                    System.out.printf("      - %s  <%s>  @%s  %s/%s%n",
                            d.getString("name"), d.getString("email"),
                            d.getString("handle"), d.getString("role"), d.getString("status"));
                }
            }
        }
    }
}
