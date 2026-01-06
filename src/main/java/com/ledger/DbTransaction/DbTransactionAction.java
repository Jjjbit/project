package com.ledger.DbTransaction;

public interface DbTransactionAction<T>{
    T execute() throws Exception;
}
