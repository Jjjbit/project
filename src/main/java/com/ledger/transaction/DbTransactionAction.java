package com.ledger.transaction;

public interface DbTransactionAction<T>{
    T execute() throws Exception;
}
