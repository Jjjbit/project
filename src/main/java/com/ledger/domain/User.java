package com.ledger.domain;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String username;
    private String password;
    public ArrayList<Ledger> ledgers;
    public List<Account> accounts;

    public User(String username, String password){
        this.username = username;
        this.password = password;
        ledgers=new ArrayList<> ();
    }
}
