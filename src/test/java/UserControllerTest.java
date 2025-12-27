import com.ledger.business.UserController;
import com.ledger.domain.User;
import com.ledger.orm.ConnectionManager;
import com.ledger.orm.UserDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class UserControllerTest {
    private Connection connection;
    private UserDAO userDAO;
    private UserController userController;

    @BeforeEach
    public void setUp() {
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        userDAO = new UserDAO(connection);
        userController = new UserController(userDAO);
    }

    private void runSchemaScript() {
        executeSqlFile("src/test/resources/schema.sql");
    }

    private void readResetScript() {
        executeSqlFile("src/test/resources/reset.sql");
    }

    private void readDataScript() {
        executeSqlFile("src/test/resources/data.sql");
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
    public void testRegister_Success() {
        assertTrue(userController.register("test user", "password"));
        assertNotNull(userDAO.getUserByUsername("test user"));
    }

    @Test
    public void testRegister_DuplicateUsername() {
        assertTrue(userController.register("duplicate user", "password1"));
        assertFalse(userController.register("duplicate user", "password2"));
    }

    @Test
    public void testLogin_Success() {
        userController.register("login user", "loginpass"); //insert user to db
        assertNotNull(userController.login("login user", "loginpass"));
    }

    @Test
    public void testLogin_Failure_WrongPassword() {
        userController.register("login user", "loginpass"); //insert user to db
        assertNull(userController.login("login user", "wrongpass"));
    }

    @Test
    public void testLogin_Failure_WrongUsername()  {
        userController.register("login user", "loginpass"); //insert user to db
        assertNull(userController.login("wrong user", "loginpass"));
    }

    @Test
    public void testLogin_Failure_NonExistentUser() {
        assertNull(userController.login("nonexistentuser", "somepass"));
    }

    @Test
    public void testUpdateUsername_Success() {
        userController.register("oldusername", "password");
        User user=userController.login("oldusername", "password");
        assertTrue(userController.updateUsername("newusername"));
        assertNotNull(userDAO.getUserByUsername("newusername"));
        assertNull(userDAO.getUserByUsername("oldusername"));
        assertEquals("newusername", user.getUsername());
    }

    @Test
    public void testUpdatePassword_Success() {
        userController.register("user2", "oldpassword");
        userController.login("user2", "oldpassword");
        assertTrue( userController.updatePassword("newpassword"));
        assertNotNull( userDAO.getUserByUsername("user2"));
    }
}
