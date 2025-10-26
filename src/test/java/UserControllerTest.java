import com.ledger.business.UserController;
import com.ledger.domain.User;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.ConnectionManager;
import com.ledger.orm.LedgerDAO;
import com.ledger.orm.UserDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class UserControllerTest {
    private Connection connection;
    private UserDAO userDAO;
    private LedgerDAO ledgerDAO;
    private AccountDAO accountDAO;
    private UserController userController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        accountDAO = new AccountDAO(connection);
        userController = new UserController(userDAO);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        readResetScript();
    }

    private void runSchemaScript() {
        try {
            Path path = Paths.get("src/test/resources/schema.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema.sql", e);
        }
    }

    private void readResetScript() throws SQLException {
        try {
            Path path = Paths.get("src/test/resources/reset.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read reset.sql", e);
        }
    }

    @Test
    public void testRegister_Success() throws SQLException {
        boolean result = userController.register("testuser", "password");
        assertTrue(result);
    }

    @Test
    public void testRegister_DuplicateUsername() throws SQLException {
        boolean firstRegistration= userController.register("duplicateuser", "password1");
        assertTrue(firstRegistration);

        boolean secondRegistration= userController.register("duplicateuser", "password2");
        assertFalse(secondRegistration);
    }

    @Test
    public void testLogin_Success() throws SQLException {
        userController.register("loginuser", "loginpass"); //insert user to db

        User loggedInUser = userController.login("loginuser", "loginpass"); //find user from db
        assertNotNull(loggedInUser);
        assertTrue(loggedInUser.getUsername().equals("loginuser"));
    }

    @Test
    public void testLogin_Failure_WrongPassword() throws SQLException {
        userController.register("loginuser", "loginpass"); //insert user to db

        User loggedInUser = userController.login("loginuser", "wrongpass"); //find user from db
        assertNull(loggedInUser);
    }
    @Test
    public void testLogin_Failure_WrongUsername() throws SQLException {
        userController.register("loginuser", "loginpass"); //insert user to db

        User loggedInUser = userController.login("wronguser", "loginpass"); //find user from db
        assertNull(loggedInUser);
    }

    @Test
    public void testLogin_Failure_NonExistentUser() throws SQLException {
        User loggedInUser = userController.login("nonexistentuser", "somepass"); //find user from db
        assertNull(loggedInUser);
    }

    @Test
    public void testUpdateUsername_Success() throws SQLException {
        userController.register("oldusername", "password");
        userController.login("oldusername", "password");

        User user = userDAO.getUserByUsername("oldusername");

        boolean updateResult = userController.updateUsername(user.getId(), "newusername");
        assertTrue(updateResult);

        User updatedUser = userDAO.getUserByUsername("newusername");
        assertNotNull(updatedUser);
        assertEquals("newusername", updatedUser.getUsername());
    }


    @Test
    public void testUpdateUserInfo_NoChanges() throws SQLException {
        userController.register("user1", "oldpassword");
        User user=userController.login("user1","oldpassword");

        boolean updateResult = userController.updateUsername(user.getId(), "user1");
        assertTrue(updateResult);
    }


}
