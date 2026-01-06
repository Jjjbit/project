import com.ledger.BusinessLogic.UserController;
import com.ledger.DomainModel.User;
import com.ledger.ORM.ConnectionManager;
import com.ledger.ORM.UserDAO;
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
    public void testRegister_Failure() {
        assertTrue(userController.register("duplicate user", "password1"));
        assertFalse(userController.register("duplicate user", "password2"));
        assertFalse(userController.register("", "password")); //empty username
        assertFalse(userController.register("a".repeat(51), "password")); //too long username
        assertFalse(userController.register("validuser", "")); //empty password
        assertFalse(userController.register("validuser", "short")); //too short password
        assertFalse(userController.register("validuser", "a".repeat(51))); //too long password
    }

    @Test
    public void testLogin_Success() {
        userController.register("login user", "loginpass"); //insert user to db
        assertNotNull(userController.login("login user", "loginpass"));
    }

    @Test
    public void testLogin_Failure() {
        userController.register("login user", "loginpass"); //insert user to db
        assertNull(userController.login("login user", "wrongpass")); //wrong password
        assertNull(userController.login("wrong user", "loginpass")); //wrong username
        assertNull(userController.login("nonexistentuser", "somepass")); //non existent user
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
    public void testUpdateUsername_Failure() {
        userController.register("existinguser", "password1");
        userController.register("oldusername", "password2");
        userController.login("oldusername", "password2");
        assertFalse(userController.updateUsername("existinguser")); //username already taken
        assertFalse(userController.updateUsername("")); //empty username
        assertFalse(userController.updateUsername("a".repeat(51))); //too long username
        userController.logout();
        assertFalse(userController.updateUsername("newusername")); //not logged in
    }

    @Test
    public void testUpdatePassword_Success() {
        userController.register("user2", "oldpassword");
        userController.login("user2", "oldpassword");
        assertTrue( userController.updatePassword("newpassword"));
        assertNotNull( userDAO.getUserByUsername("user2"));
    }

    @Test
    public void testUpdatePassword_Failure() {
        userController.register("user3", "oldpassword");
        userController.login("user3", "oldpassword");
        assertFalse( userController.updatePassword("")); //empty password
        assertFalse( userController.updatePassword("short")); //too short password
        assertFalse( userController.updatePassword("a".repeat(51))); //too long password
        userController.logout();
        assertFalse( userController.updatePassword("newpassword")); //not logged in
    }

}
