package com.ledger.BusinessLogic;

import com.ledger.DomainModel.Account;
import com.ledger.DomainModel.Transaction;
import com.ledger.DomainModel.User;
import com.ledger.ORM.AccountDAO;
import com.ledger.ORM.TransactionDAO;
import com.ledger.Session.UserSession;
import com.ledger.DbTransaction.DbTransactionManager;

import java.math.BigDecimal;
import java.util.List;

public class AccountController {
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public AccountController(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    public List<Account> getAccounts(User user) {
        return accountDAO.getAccountsByOwner(user);
    }
    public List<Account> getSelectableAccounts(User user) {
        return getAccounts(user).stream()
                .filter(Account::getSelectable)
                .toList();
    }

    public Account createAccount(String name, BigDecimal balance, boolean includedInAsset, boolean selectable) {
        if(!UserSession.getInstance().isLoggedIn()) return null;
        if (name == null || name.isEmpty() || name.length() > 50) return null;
        if (balance == null ) balance = BigDecimal.ZERO;
        User owner = UserSession.getInstance().getCurrentUser();
        Account account = new Account(name, balance, owner, includedInAsset, selectable);
        if(accountDAO.insert(account)){
            return account;
        } else {
            return null;
        }
    }

    public boolean deleteAccount(Account account) {
        Boolean deleted = DbTransactionManager.getInstance().execute(() -> {
            List<Transaction> linkedTransactions = transactionDAO.getByAccountId(account.getId());
            for (Transaction tx : linkedTransactions) {
                Account fromAccount = tx.getFromAccount();
                Account toAccount = tx.getToAccount();
                    if(fromAccount == null && toAccount.getId() == account.getId()){
                        if(!transactionDAO.delete(tx)) throw new Exception("Failed to delete linked transaction");
                    }
                    if(toAccount == null && fromAccount.getId() == account.getId()){
                        if(!transactionDAO.delete(tx)) throw new Exception("Failed to delete linked transaction");
                    }
            }
            if(!accountDAO.delete(account)) throw new Exception("Failed to delete account");
            return true;
        });
        return deleted != null && deleted;
    }

    public boolean editAccount(Account account, String newName, BigDecimal newBalance, boolean newIncludedInAsset, boolean newSelectable) {
        if(newName == null || newBalance == null) return false;
        if(newName.isEmpty() || newName.length() > 50) return false;
        account.setName(newName);
        account.setBalance(newBalance);
        account.setIncludedInAsset(newIncludedInAsset);
        account.setSelectable(newSelectable);
        return accountDAO.update(account);
    }
}
