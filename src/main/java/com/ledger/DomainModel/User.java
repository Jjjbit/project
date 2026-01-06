package com.ledger.DomainModel;

public class User {
    private long id;
    private String username;
    private String password;
    //private List<Ledger> ledgers= new ArrayList<>();
    //private List<Account> accounts= new ArrayList<>();

    public User (){}
    public User(String username, String password){
        this.username = username;
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setId(long id) {
        this.id = id;
    }
    public long getId() {
        return id;
    }
    public String getPassword(){return password;}
    public String getUsername(){return username;}

}



