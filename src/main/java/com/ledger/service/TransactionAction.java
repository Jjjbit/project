package com.ledger.service;

public interface TransactionAction <T>{
    T execute() throws Exception;
}
