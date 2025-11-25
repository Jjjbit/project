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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class UserControllerTest {
    private Connection connection;
    private UserDAO userDAO;
    private UserController userController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
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
        boolean result = userController.register("test user", "password");
        assertTrue(result);
        assertNotNull(userDAO.getUserByUsername("test user"));
    }

    @Test
    public void testRegister_DuplicateUsername() {
        boolean firstRegistration= userController.register("duplicate user", "password1");
        assertTrue(firstRegistration);

        boolean secondRegistration= userController.register("duplicate user", "password2");
        assertFalse(secondRegistration);
    }

    @Test
    public void testLogin_Success() {
        userController.register("login user", "loginpass"); //insert user to db

        User loggedInUser = userController.login("login user", "loginpass"); //find user from db
        assertNotNull(loggedInUser);
        assertEquals("login user", loggedInUser.getUsername());
    }

    @Test
    public void testLogin_Failure_WrongPassword() {
        userController.register("login user", "loginpass"); //insert user to db

        User loggedInUser = userController.login("login user", "wrongpass"); //find user from db
        assertNull(loggedInUser);
    }

    @Test
    public void testLogin_Failure_WrongUsername()  {
        userController.register("login user", "loginpass"); //insert user to db

        User loggedInUser = userController.login("wrong user", "loginpass"); //find user from db
        assertNull(loggedInUser);
    }

    @Test
    public void testLogin_Failure_NonExistentUser() {
        User loggedInUser = userController.login("nonexistentuser", "somepass"); //find user from db
        assertNull(loggedInUser);
    }

    @Test
    public void testUpdateUsername_Success() {
        userController.register("oldusername", "password");
        User user=userController.login("oldusername", "password");

        boolean updateResult = userController.updateUsername("newusername");
        assertTrue(updateResult);

        User updatedUser = userDAO.getUserByUsername("newusername");
        assertNotNull(updatedUser);
        assertNull(userDAO.getUserByUsername("oldusername"));
        assertEquals("newusername", user.getUsername());
    }


    @Test
    public void testUpdateUserInfo_NoChanges() {
        userController.register("user1", "oldpassword");
        userController.login("user1","oldpassword");

        boolean updateResult = userController.updateUsername("user1");
        assertTrue(updateResult);
    }

    @Test
    public void testUpdatePassword_Success() {
        userController.register("user2", "oldpassword");
        User user=userController.login("user2","oldpassword");

        boolean updateResult = userController.updatePassword("newpassword");
        assertTrue(updateResult);

        User updatedUser = userDAO.getUserByUsername("user2");
        assertNotNull(updatedUser);
        assertEquals(updatedUser.getPassword(), user.getPassword());
    }
}
