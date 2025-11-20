import com.ledger.domain.Category;
import com.ledger.domain.CategoryType;
import com.ledger.orm.CategoryDAO;
import com.ledger.orm.ConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CategoryDAOTest {
    private Connection connection;
    private CategoryDAO categoryDAO;

    @BeforeEach
    public void setUp() throws SQLException {
        connection=ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        categoryDAO = new CategoryDAO(connection);
    }

    private void runSchemaScript() {
        executeSqlFile("src/test/resources/schema.sql");
    }
    private void readDataScript() {
        executeSqlFile("src/test/resources/data.sql");
    }

    private void readResetScript() {
        executeSqlFile("src/test/resources/reset.sql");
    }

    private void executeSqlFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String sql;
            try (Stream<String> lines = Files.lines(path)) {
                sql = lines.collect(Collectors.joining("\n"));
            }

            try (Statement stmt = connection.createStatement()) {
                for (String s : sql.split(";")) {
                    if (!s.trim().isEmpty()) {
                        stmt.execute(s);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute " + filePath, e);
        }
    }

    @Test
    public void testGetCategoryTree() throws SQLException {
        List<Category> categories = categoryDAO.getCategoryTree();
        assert(categories != null && !categories.isEmpty());
        assertEquals(17, categories.size()); // assuming 17 is the expected number of root categories

        List<Category> expenseRoots = categories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .filter(cat -> cat.getParent() == null)
                .toList();

        List<Category> incomeRoots = categories.stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .filter(cat -> cat.getParent() == null)
                .toList();

        System.out.println("Expense Categories:");
        for (Category expenseRoot : expenseRoots) {
            System.out.println("- " + expenseRoot.getName());
            for (Category sub: categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == expenseRoot.getId())
                    .toList()) {
                System.out.println("  - " + sub.getName());
            }
        }
        System.out.println("Income Categories:");
        for (Category incomeRoot : incomeRoots) {
            System.out.println("- " + incomeRoot.getName());
            for (Category sub: categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == incomeRoot.getId())
                    .toList()) {
                System.out.println("  - " + sub.getName());
            }
        }

    }
}
